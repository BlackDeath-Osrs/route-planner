package com.routeplanner.agility;

import lombok.Data;
import java.util.List;

@Data
public class AgilityCourse {
    private final String name;
    private final int levelRequired;
    private final double xpPerLap;
    private final List<AgilityObstacle> obstacles;
}
