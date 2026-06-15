package com.routeplanner.agility;

import lombok.Data;

@Data
public class AgilityTask {
    private final AgilityCourse course;
    private final GoalType goalType;
    private final long goalValue;
    private int currentObstacleIndex = 0;
    private int lapsCompleted = 0;

    public AgilityObstacle getCurrentObstacle() {
        if (course == null || course.getObstacles().isEmpty()) return null;
        return course.getObstacles().get(currentObstacleIndex);
    }

    public void advanceObstacle() {
        currentObstacleIndex++;
        if (currentObstacleIndex >= course.getObstacles().size()) {
            currentObstacleIndex = 0;
            lapsCompleted++;
        }
    }

    public boolean isComplete(int currentLevel, long currentXp) {
        switch (goalType) {
            case TARGET_LEVEL: return currentLevel >= goalValue;
            case TARGET_XP:    return currentXp >= goalValue;
            case LAPS:         return lapsCompleted >= goalValue;
            default:           return false;
        }
    }

    public String getProgressString(int currentLevel, long currentXp) {
        switch (goalType) {
            case TARGET_LEVEL: return "Level " + currentLevel + " / " + goalValue;
            case TARGET_XP:    return String.format("%,d / %,d xp", currentXp, goalValue);
            case LAPS:         return lapsCompleted + " / " + goalValue + " laps";
            default:           return "";
        }
    }
}
