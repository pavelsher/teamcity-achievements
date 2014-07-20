package org.jetbrains.buildserver.achievements.impl;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.SecurityContextEx;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.users.PluginPropertyKey;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.buildserver.achievements.AchievementsConfig;
import org.jetbrains.buildserver.achievements.UserEvents;
import org.jetbrains.buildserver.achievements.UserEventsListener;
import org.jetbrains.buildserver.achievements.UserEventsRegistry;

import java.util.*;

public class AchievementsGrantor implements UserEventsListener {
  private final static Logger LOG = Logger.getInstance(AchievementsGrantor.class.getName());

  private final AchievementsConfig myConfig;
  private final UserEventsRegistry myEventsRegistry;
  private final UserModel myUserModel;
  private final SecurityContextEx mySecurityContext;

  public AchievementsGrantor(@NotNull AchievementsConfig config,
                             @NotNull UserEventsRegistry userEventsRegistry,
                             @NotNull UserModel userModel,
                             @NotNull SecurityContextEx securityContext) {
    myConfig = config;
    myEventsRegistry = userEventsRegistry;
    myUserModel = userModel;
    mySecurityContext = securityContext;
    userEventsRegistry.addListener(this);
  }

  public void userEventPublished(@NotNull User user, @NotNull String eventName, @Nullable Object additionalData) {
    UserEvents events = myEventsRegistry.getUserEvents(user);
    for (Achievement achievement: myConfig.getAchievements()) {
      if (hasAchievement(user, achievement)) continue;

      if (achievement.shouldGrantAchievement((SUser) user, events, eventName, additionalData)) {
        grantAchievement(user, achievement);
      }
    }
  }

  public boolean hasAchievement(@NotNull User user, @NotNull Achievement achievement) {
    return user.getPropertyValue(makePropertyKey(achievement)) != null;
  }

  public void grantAchievement(@NotNull User user, @NotNull Achievement achievement) {
    SUser su = (SUser) user;
    su.setUserProperty(makePropertyKey(achievement), String.valueOf(new Date().getTime()));
    LOG.info("User " + LogUtil.describe(user) + " has been granted achievement: " + achievement.getName());
  }

  @NotNull
  public List<Achievement> getGrantedAchievements(@NotNull User user) {
    List<Achievement> res = new ArrayList<Achievement>();
    for (Achievement achievement: myConfig.getAchievements()) {
      if (hasAchievement(user, achievement)) {
        res.add(achievement);
      }
    }

    return res;
  }

  @NotNull
  public Map<Achievement, List<SUser>> getAchievementUsersMap() {
    try {
      return mySecurityContext.runAsSystem(new SecurityContextEx.RunAsActionWithResult<Map<Achievement, List<SUser>>>() {
        public Map<Achievement, List<SUser>> run() throws Throwable {
          Map<Achievement, List<SUser>> res = new HashMap<Achievement, List<SUser>>();
          for (SUser user: myUserModel.getAllUsers().getUsers()) {
            for (Achievement achievement: myConfig.getAchievements()) {
              List<SUser> users = res.get(achievement);
              if (users == null) {
                users = new ArrayList<SUser>();
                res.put(achievement, users);
              }
              if (hasAchievement(user, achievement)) {
                users.add(user);
              }
            }

          }
          return res;
        }
      });
    } catch (Throwable throwable) {
      ExceptionUtil.rethrowAsRuntimeException(throwable);
    }
    return Collections.emptyMap();
  }

  @NotNull
  private PluginPropertyKey makePropertyKey(@NotNull Achievement achievement) {
    return new PluginPropertyKey("achievements", "achievements", "achievement.granted." + achievement.getId());
  }

}
