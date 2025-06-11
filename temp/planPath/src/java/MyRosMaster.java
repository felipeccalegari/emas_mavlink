import embedded.mas.bridges.ros.IRosInterface;
import embedded.mas.bridges.ros.RosMaster;
import embedded.mas.bridges.ros.DefaultRos4EmbeddedMas;

import jason.asSyntax.Atom;
import jason.asSyntax.ListTermImpl;
import jason.asSyntax.Literal;
import jason.asSyntax.NumberTermImpl;
import jason.asSyntax.Term;
import jason.asSemantics.Unifier;

public class MyRosMaster extends RosMaster{

    public MyRosMaster(Atom id, IRosInterface microcontroller) {
        super(id, microcontroller);
    }
    

    @Override
	public boolean execEmbeddedAction(String actionName, Object[] args, Unifier un) {		
		//execute the actions configured in the yaml file
        super.execEmbeddedAction(actionName, args, un);  // <- do not delete this line

		//Execute a customized actions 
          
		// The action "update_value" is realized through the writing in 2 topics */
		// Parameters of the method rosWrite:
		//     - topic name
		//     - topic type
		//     - value to be written in the topic
		if(actionName.equals("plan_path")){ // <- test is the name of the internal action		  
            ListTermImpl header = (ListTermImpl)args[0]; //first list of parameters - correspond to the field header
            Term header_seq = header.get(0);
            ListTermImpl header_stamp = (ListTermImpl)header.get(1); //the second element of header is a list
            Term header_stamp_secs = header_stamp.get(0);
            Term header_stamp_nsecs = header_stamp.get(1);
            Term header_frame_id = header.get(2);

            ListTermImpl goal_id = (ListTermImpl)args[1]; //the value of the field goal_id is the second parameter (also a list)
            ListTermImpl goal_id_stamp = (ListTermImpl)goal_id.get(0);
            Term goal_id_stamp_secs = goal_id_stamp.get(0);
            Term goal_id_stamp_nsecs = goal_id_stamp.get(1);
            Term goal_id_id = goal_id.get(1);

            ListTermImpl goal = (ListTermImpl)args[2]; //the value of the field goal is the 3rd parameter (also a list)
            ListTermImpl goal_property = (ListTermImpl) goal.get(0);
            ListTermImpl goal_property_header = (ListTermImpl) goal_property.get(0);
            Term goal_property_header_seq = goal_property_header.get(0);
            ListTermImpl goal_property_header_stamp = (ListTermImpl) goal_property_header.get(1);
            Term goal_property_header_stamp_secs = goal_property_header_stamp.get(0);
            Term goal_property_header_stamp_nsecs = goal_property_header_stamp.get(1);
            Term goal_property_header_frame_id = goal_property_header.get(2);

            ListTermImpl goal_property_polygon = (ListTermImpl) goal_property.get(1);
            ListTermImpl goal_property_polygon_points = (ListTermImpl) goal_property_polygon.get(0);
            ListTermImpl goal_property_polygon_points_1 = (ListTermImpl) goal_property_polygon_points.get(0);
            Term goal_property_polygon_points_1_x = goal_property_polygon_points_1.get(0);
            Term goal_property_polygon_points_1_y = goal_property_polygon_points_1.get(1);
            Term goal_property_polygon_points_1_z = goal_property_polygon_points_1.get(2);
            ListTermImpl goal_property_polygon_points_2 = (ListTermImpl) goal_property_polygon_points.get(1);
            Term goal_property_polygon_points_2_x = goal_property_polygon_points_2.get(0);
            Term goal_property_polygon_points_2_y = goal_property_polygon_points_2.get(1);
            Term goal_property_polygon_points_2_z = goal_property_polygon_points_2.get(2);
            ListTermImpl goal_property_polygon_points_3 = (ListTermImpl) goal_property_polygon_points.get(2);
            Term goal_property_polygon_points_3_x = goal_property_polygon_points_3.get(0);
            Term goal_property_polygon_points_3_y = goal_property_polygon_points_3.get(1);
            Term goal_property_polygon_points_3_z = goal_property_polygon_points_3.get(2);
            ListTermImpl goal_property_polygon_points_4 = (ListTermImpl) goal_property_polygon_points.get(3);
            Term goal_property_polygon_points_4_x = goal_property_polygon_points_4.get(0);
            Term goal_property_polygon_points_4_y = goal_property_polygon_points_4.get(1);
            Term goal_property_polygon_points_4_z = goal_property_polygon_points_4.get(2);

            ListTermImpl goal_robot_position = (ListTermImpl) goal.get(1);
            ListTermImpl goal_robot_position_header = (ListTermImpl) goal_robot_position.get(0);
            Term goal_robot_position_header_seq =  goal_robot_position_header.get(0);
            ListTermImpl goal_robot_position_header_stamp = (ListTermImpl) goal_robot_position_header.get(1);
            Term goal_robot_position_header_stamp_secs = goal_robot_position_header_stamp.get(0);
            Term goal_robot_position_header_stamp_nsecs = goal_robot_position_header_stamp.get(1);
            Term goal_robot_position_header_frame_id = goal_robot_position_header.get(2);

            ListTermImpl goal_robot_position_pose = (ListTermImpl) goal_robot_position.get(1);
            ListTermImpl goal_robot_position_pose_position = (ListTermImpl)goal_robot_position_pose.get(0);
            Term goal_robot_position_pose_position_x = goal_robot_position_pose_position.get(0);
            Term goal_robot_position_pose_position_y = goal_robot_position_pose_position.get(1);
            Term goal_robot_position_pose_position_z = goal_robot_position_pose_position.get(2);

            ListTermImpl goal_robot_position_pose_orientation = (ListTermImpl)goal_robot_position_pose.get(1);
            Term goal_robot_position_pose_orientation_x = goal_robot_position_pose_orientation.get(0);
            Term goal_robot_position_pose_orientation_y = goal_robot_position_pose_orientation.get(1);
            Term goal_robot_position_pose_orientation_z = goal_robot_position_pose_orientation.get(2);
            Term goal_robot_position_pose_orientation_w = goal_robot_position_pose_orientation.get(3);

            
            String valueToPublish = "{\"header\":{\"seq\":"+header_seq+"," +
                                                  "\"stamp\":{\"secs\":"+header_stamp_secs+ "," + 
                                                             "\"nsecs\":"+header_stamp_nsecs+"}," +
                                                  "\"frame_id\":"+header_frame_id+"}," + 
                                      "\"goal_id\":{\"stamp\":{\"secs\":"+ goal_id_stamp_secs +"," + 
                                                              "\"nsecs\":"+ goal_id_stamp_nsecs +"}," +
                                                   "\"id\":"+goal_id_id+"}," + 
                                      "\"goal\":{\"property\":{\"header\":{\"seq\":"+ goal_property_header_seq +"," + 
                                                                          "\"stamp\":{\"secs\":"+ goal_property_header_stamp_secs +"," + 
                                                                                     "\"nsecs\":"+ goal_property_header_stamp_nsecs +"}," +
                                                                          "\"frame_id\": "+ goal_property_header_frame_id +"}, " + 
                                                               "\"polygon\":{\"points\":[{\"x\":"+ goal_property_polygon_points_1_x +"," + 
                                                                                        "\"y\":"+ goal_property_polygon_points_1_y +"," +
                                                                                        "\"z\":"+ goal_property_polygon_points_1_z+"}," + 
                                                                                       "{\"x\":"+ goal_property_polygon_points_2_x +"," + 
                                                                                        "\"y\":"+ goal_property_polygon_points_2_y +"," +
                                                                                        "\"z\":"+ goal_property_polygon_points_2_z+"}," +
                                                                                       "{\"x\":"+ goal_property_polygon_points_3_x +"," + 
                                                                                        "\"y\":"+ goal_property_polygon_points_3_y +"," +
                                                                                        "\"z\":"+ goal_property_polygon_points_3_z+"}," +
                                                                                       "{\"x\":"+ goal_property_polygon_points_4_x +"," + 
                                                                                        "\"y\":"+ goal_property_polygon_points_4_y +"," +
                                                                                        "\"z\":"+ goal_property_polygon_points_4_z+"}]}}," + 
                                                 "\"robot_position\":{\"header\":{\"seq\":"+ goal_robot_position_header_seq +"," + 
                                                                                 "\"stamp\":{\"secs\":"+ goal_robot_position_header_stamp_secs +"," +
                                                                                            "\"nsecs\":"+ goal_robot_position_header_stamp_nsecs +"}," +
                                                                                  "\"frame_id\":"+ goal_robot_position_header_frame_id +"}," + 
                                                                     "\"pose\":{\"position\":{\"x\":"+ goal_robot_position_pose_position_x +"," + 
                                                                                             "\"y\":"+ goal_robot_position_pose_position_y +"," +
                                                                                             "\"z\":"+ goal_robot_position_pose_position_z +"}," + 
                                                                               "\"orientation\":{\"x\":"+ goal_robot_position_pose_orientation_x +"," +
                                                                                                "\"y\":"+ goal_robot_position_pose_orientation_y +"," + 
                                                                                                "\"z\":"+ goal_robot_position_pose_orientation_z +"," +
                                                                                                "\"w\":"+ goal_robot_position_pose_orientation_w +"}}}}}"; 
            String topicName = "/plan_path/goal";
            String topicType = "boustrophedon_msgs/PlanMowingPathActionGoal";



            //publishing in the topic
		    ((DefaultRos4EmbeddedMas) this.getMicrocontroller()).rosWrite(topicName,topicType,valueToPublish);
		
		}

		

		return true;
	}

}