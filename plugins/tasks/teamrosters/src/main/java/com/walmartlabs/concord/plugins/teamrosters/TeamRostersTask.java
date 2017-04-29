package com.walmartlabs.concord.plugins.teamrosters;

import com.walmartlabs.concord.common.Task;
import com.walmartlabs.looper.teamrosters.client.TeamRosters;
import com.walmartlabs.looper.teamrosters.client.TeamRostersClient;
import com.walmartlabs.looper.teamrosters.model.TrUserDetails;

import java.io.IOException;

import javax.inject.Named;

@Named("teamRosters")
public class TeamRostersTask implements Task {

  private final TeamRosters client;
  
  public TeamRostersTask() {
    client = TeamRostersClient.builder().build();    
  }
  
  public String costCenterForUser(String username) {
    try {
      TrUserDetails user = client.fetchUserDetails(username);
      return user.getDeptNum();
    } catch (IOException e) {
      return "0000";
    }    
  }
}
