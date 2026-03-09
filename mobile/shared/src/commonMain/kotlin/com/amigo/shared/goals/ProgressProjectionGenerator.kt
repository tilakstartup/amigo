package com.amigo.shared.goals

import kotlinx.serialization.Serializable
import kotlin.math.ceil

/**
 * Generates progress projection data for visualization
 * Creates timeline data for charts and graphs
 */
class ProgressProjectionGenerator(
    private val calculationEngine: GoalCalculationEngine
) {
    
    /**
     * Generate complete progress projection for a goal
     */
    fun generateProjection(plan: GoalPlan): ProgressProjection {
        val milestones = plan.milestones
        val dataPoints = milestones.map { milestone ->
            ProjectionDataPoint(
                week = milestone.week,
                weightKg = milestone.projectedWeightKg,
                bmiValue = calculationEngine.calculateBMI(
                    milestone.projectedWeightKg,
                    plan.currentMetrics.heightCm
                ),
                percentComplete = milestone.percentComplete,
                cumulativeWeightLoss = milestone.cumulativeWeightLossKg
            )
        }
        
        return ProgressProjection(
            dataPoints = dataPoints,
            startWeight = plan.currentMetrics.weightKg,
            targetWeight = plan.targetMetrics.weightKg,
            startBMI = plan.currentMetrics.bmi,
            targetBMI = plan.targetMetrics.targetBMI,
            totalWeeks = milestones.size - 1,
            weeklyRate = plan.calculations.weeklyWeightLossRate
        )
    }
    
    /**
     * Generate monthly summary projections
     */
    fun generateMonthlySummary(plan: GoalPlan): List<MonthlyProjection> {
        val milestones = plan.milestones
        val monthlyProjections = mutableListOf<MonthlyProjection>()
        
        var currentMonth = 0
        var monthStartWeight = plan.currentMetrics.weightKg
        
        for (i in milestones.indices step 4) { // Every 4 weeks = ~1 month
            if (i >= milestones.size) break
            
            val milestone = milestones[i]
            val monthEndWeight = milestone.projectedWeightKg
            val weightLoss = monthStartWeight - monthEndWeight
            
            monthlyProjections.add(
                MonthlyProjection(
                    month = currentMonth + 1,
                    startWeight = monthStartWeight,
                    endWeight = monthEndWeight,
                    weightLoss = weightLoss,
                    averageDailyCalories = plan.calculations.dailyCalories,
                    bmiAtEnd = calculationEngine.calculateBMI(
                        monthEndWeight,
                        plan.currentMetrics.heightCm
                    )
                )
            )
            
            monthStartWeight = monthEndWeight
            currentMonth++
        }
        
        return monthlyProjections
    }
    
    /**
     * Generate chart data for weight progression
     */
    fun generateWeightChartData(plan: GoalPlan): ChartData {
        val labels = plan.milestones.map { "Week ${it.week}" }
        val values = plan.milestones.map { it.projectedWeightKg }
        
        return ChartData(
            labels = labels,
            values = values,
            unit = "kg",
            title = "Weight Progression",
            minValue = plan.targetMetrics.weightKg - 2,
            maxValue = plan.currentMetrics.weightKg + 2
        )
    }
    
    /**
     * Generate chart data for BMI progression
     */
    fun generateBMIChartData(plan: GoalPlan): ChartData {
        val labels = plan.milestones.map { "Week ${it.week}" }
        val values = plan.milestones.map { milestone ->
            calculationEngine.calculateBMI(
                milestone.projectedWeightKg,
                plan.currentMetrics.heightCm
            )
        }
        
        return ChartData(
            labels = labels,
            values = values,
            unit = "BMI",
            title = "BMI Progression",
            minValue = plan.targetMetrics.targetBMI - 2,
            maxValue = plan.currentMetrics.bmi + 2
        )
    }
    
    /**
     * Generate chart data for calorie deficit
     */
    fun generateCalorieDeficitChartData(plan: GoalPlan): ChartData {
        val weeks = plan.milestones.size - 1
        val labels = (0..weeks).map { "Week $it" }
        val dailyDeficit = plan.calculations.calorieDeficitPerDay
        val values = labels.map { dailyDeficit }
        
        return ChartData(
            labels = labels,
            values = values,
            unit = "cal/day",
            title = "Daily Calorie Deficit",
            minValue = 0.0,
            maxValue = dailyDeficit + 200
        )
    }
    
    /**
     * Generate milestone markers for timeline view
     */
    fun generateMilestoneMarkers(plan: GoalPlan): List<MilestoneMarker> {
        val markers = mutableListOf<MilestoneMarker>()
        val totalWeightLoss = plan.calculations.totalWeightToLose
        
        // Add start marker
        markers.add(
            MilestoneMarker(
                week = 0,
                title = "Start",
                description = "Current weight: ${plan.currentMetrics.weightKg.toString().take(5)} kg",
                isAchieved = true
            )
        )
        
        // Add 25% marker
        val week25 = ceil(plan.milestones.size * 0.25).toInt()
        if (week25 < plan.milestones.size) {
            val weight25 = plan.milestones[week25].projectedWeightKg
            markers.add(
                MilestoneMarker(
                    week = week25,
                    title = "25% Complete",
                    description = "Weight: ${weight25.toString().take(5)} kg",
                    isAchieved = false
                )
            )
        }
        
        // Add 50% marker
        val week50 = ceil(plan.milestones.size * 0.5).toInt()
        if (week50 < plan.milestones.size) {
            val weight50 = plan.milestones[week50].projectedWeightKg
            markers.add(
                MilestoneMarker(
                    week = week50,
                    title = "Halfway There!",
                    description = "Weight: ${weight50.toString().take(5)} kg",
                    isAchieved = false
                )
            )
        }
        
        // Add 75% marker
        val week75 = ceil(plan.milestones.size * 0.75).toInt()
        if (week75 < plan.milestones.size) {
            val weight75 = plan.milestones[week75].projectedWeightKg
            markers.add(
                MilestoneMarker(
                    week = week75,
                    title = "75% Complete",
                    description = "Weight: ${weight75.toString().take(5)} kg",
                    isAchieved = false
                )
            )
        }
        
        // Add goal marker
        markers.add(
            MilestoneMarker(
                week = plan.milestones.size - 1,
                title = "Goal Achieved!",
                description = "Target weight: ${plan.targetMetrics.weightKg.toString().take(5)} kg",
                isAchieved = false
            )
        )
        
        return markers
    }
}

/**
 * Complete progress projection with all data points
 */
@Serializable
data class ProgressProjection(
    val dataPoints: List<ProjectionDataPoint>,
    val startWeight: Double,
    val targetWeight: Double,
    val startBMI: Double,
    val targetBMI: Double,
    val totalWeeks: Int,
    val weeklyRate: Double
)

/**
 * Single data point in the projection
 */
@Serializable
data class ProjectionDataPoint(
    val week: Int,
    val weightKg: Double,
    val bmiValue: Double,
    val percentComplete: Double,
    val cumulativeWeightLoss: Double
)

/**
 * Monthly summary projection
 */
@Serializable
data class MonthlyProjection(
    val month: Int,
    val startWeight: Double,
    val endWeight: Double,
    val weightLoss: Double,
    val averageDailyCalories: Double,
    val bmiAtEnd: Double
)

/**
 * Chart data for visualization
 */
@Serializable
data class ChartData(
    val labels: List<String>,
    val values: List<Double>,
    val unit: String,
    val title: String,
    val minValue: Double,
    val maxValue: Double
)

/**
 * Milestone marker for timeline
 */
@Serializable
data class MilestoneMarker(
    val week: Int,
    val title: String,
    val description: String,
    val isAchieved: Boolean
)
