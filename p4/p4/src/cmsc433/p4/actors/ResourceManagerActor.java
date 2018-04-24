package cmsc433.p4.actors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.accessibility.AccessibleStreamable;

import cmsc433.p4.enums.*;
import cmsc433.p4.messages.*;
import cmsc433.p4.util.*;
import scala.collection.mutable.HashTable;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;

public class ResourceManagerActor extends UntypedActor {
	
	private ActorRef logger;					// Actor to send logging messages to
	
	/**
	 * Props structure-generator for this class.
	 * @return  Props structure
	 */
	static Props props (ActorRef logger) {
		return Props.create(ResourceManagerActor.class, logger);
	}
	
	/**
	 * Factory method for creating resource managers
	 * @param logger			Actor to send logging messages to
	 * @param system			Actor system in which manager will execute
	 * @return					Reference to new manager
	 */
	public static ActorRef makeResourceManager (ActorRef logger, ActorSystem system) {
		ActorRef newManager = system.actorOf(props(logger));
		return newManager;
	}
	
	/**
	 * Sends a message to the Logger Actor
	 * @param msg The message to be sent to the logger
	 */
	public void log (LogMsg msg) {
		logger.tell(msg, getSelf());
	}
	
	/**
	 * Constructor
	 * 
	 * @param logger			Actor to send logging messages to
	 */
	private ResourceManagerActor(ActorRef logger) {
		super();
		this.logger = logger;
	}

	// You may want to add data structures for managing local resources and users, storing
	// remote managers, etc.
	//
	// REMEMBER:  YOU ARE NOT ALLOWED TO CREATE MUTABLE DATA STRUCTURES THAT ARE SHARED BY
	// MULTIPLE ACTORS!
	
	/* (non-Javadoc)
	 * 
	 * You must provide an implementation of the onReceive() method below.
	 * 
	 * @see akka.actor.UntypedActor#onReceive(java.lang.Object)
	 */
	
	
	/* Important fields for each Resource Manager*/
	private ArrayList<Resource> local_resources;
	private ArrayList<ActorRef> local_users;
	private ArrayList<ActorRef> remote_resource_managers;
	private HashMap<Resource,ActorRef> res_to_man;
	
	private HashMap<String, Resource> resource_names;
	private HashMap<Resource,Integer> rm_res;
	
	/* Remote Resources Request Information; keys correspond to resource name  */
	private HashMap<String, Object> remote_msg = new HashMap<>();
	private HashMap<String, ActorRef> res_requester = new HashMap<>();
	private HashMap<String, Integer> remote_awaits = new HashMap<>();
	/* ************************************* */
	
	@Override
	public void onReceive(Object msg) throws Exception {		
		
		/* Initial messages for System Initiation */
		
		// Messages asking resource-manager to add given local resources
		if(msg instanceof AddInitialLocalResourcesRequestMsg) {
			// Retrieve the resources
			AddInitialLocalResourcesRequestMsg init_lr_req = (AddInitialLocalResourcesRequestMsg) msg;
			local_resources = new ArrayList<>(init_lr_req.getLocalResources());
			AddInitialLocalResourcesResponseMsg reply_msg = new AddInitialLocalResourcesResponseMsg(init_lr_req);
			
			// Initialize the name reference to resource table
			resource_names = new HashMap<>();
			
			// Enable to resources for use
			for (Resource resource : local_resources) {
				resource.enable();
				res_to_man.put(resource, getSelf());
				resource_names.put(resource.name, resource);
			}
			// Respond to Sender
			getSender().tell(reply_msg, getSelf());
		}
		// Message requesting addition of local users to a resource manager
		else if(msg instanceof AddLocalUsersRequestMsg) {
			// Retrieve local users to this resource manager
			AddLocalUsersRequestMsg init_local_user_req = (AddLocalUsersRequestMsg) msg;
			local_users = new ArrayList<>(init_local_user_req.getLocalUsers());
			AddLocalUsersResponseMsg reply_msg = new AddLocalUsersResponseMsg(init_local_user_req);
			// Respond to sender
			getSender().tell(reply_msg, getSelf());
		}
		// Messages requesting addition of remote managers to given resource manager
		else if(msg instanceof AddRemoteManagersRequestMsg) {
			// Retrieve list of all Resource Managers
			AddRemoteManagersRequestMsg init_remote_managers_req = (AddRemoteManagersRequestMsg) msg;
			remote_resource_managers = new ArrayList<>(init_remote_managers_req.getManagerList());
			AddRemoteManagersResponseMsg reply_msg = new AddRemoteManagersResponseMsg(init_remote_managers_req);
			getSelf().tell(reply_msg, getSelf());
		}
		
		/*Messages for Request Forwarding*/
		
		// Message requesting the manager of a resource
		else if(msg instanceof WhoHasResourceRequestMsg) {
			// Retrieve the message details
			WhoHasResourceRequestMsg check_lr_req = (WhoHasResourceRequestMsg) msg;
			
			if(resource_names.containsKey(check_lr_req.getResourceName())) {
				WhoHasResourceResponseMsg reply_msg = new WhoHasResourceResponseMsg(check_lr_req.getResourceName(), true, getSelf());
				getSender().tell(reply_msg,getSelf());
			}else {
				WhoHasResourceResponseMsg reply_msg = new WhoHasResourceResponseMsg(check_lr_req.getResourceName(), false, getSelf());
				getSender().tell(reply_msg,getSelf());
			}
		}
		// Message for responses of managers answering if the have resources
		else if(msg instanceof WhoHasResourceResponseMsg) {
			WhoHasResourceResponseMsg rm_res = (WhoHasResourceResponseMsg) msg;
			if(rm_res.getResult() == true){
				// Forward the message to the Resource Manager
				getSender().tell(remote_msg.get(rm_res.getResourceName()), res_requester.get(rm_res.getResourceName()));
				// Remove the remote information about this resource
				remote_msg.remove(rm_res.getResourceName());
				res_requester.remove(rm_res.getResourceName());
				remote_awaits.remove(rm_res.getResourceName());
			}else {
				// Check if we still need to find the resource manager
				if(remote_msg.containsKey(rm_res.getResourceName()) && res_requester.containsKey(rm_res.getResourceName()) && remote_awaits.containsKey(rm_res.getResourceName())) {
					// Decrement the about of awaiting responses
					remote_awaits.put(rm_res.getResourceName(), remote_awaits.get(rm_res.getResourceName())-1);
					
					// Check if resource manager is not found
					if(remote_awaits.get(rm_res.getResourceName()) == 0) {
						// No resource manager manages that resource
						// Re-send the message to self as user, to reply access denied once instance is known 
						getSelf().tell(remote_msg.get(rm_res.getResourceName()), res_requester.get(rm_res.getResourceName()));		
					}
				}
			}			
		}
		
		else {
			// Message was unhandled
			unhandled(msg);
		}
	}
}
