# ---- Load Required Libraries ----
library(tidyverse)
library(lme4)
library(lmerTest)
library(mixedpower)
library(performance)
library(report)        
library(readr)
library(rstudioapi)    
library(emmeans)       
library(brms)          
library(bayestestR)   
library(xtable)    
library(survival)
library(survminer)
library(emmeans)
library(tidybayes)

# ---- Helper Functions ----
# Numeric variable summary (min, max, mean, median, sd)
describe_factor <- function(df, factor_name) {
  if (!factor_name %in% names(df)) stop(paste("Variable", factor_name, "not found in data frame"))
  var_data <- df[[factor_name]]
  if (!is.numeric(var_data)) stop(paste("Variable", factor_name, "is not numeric"))
  tibble(
    variable = factor_name,
    min      = min(var_data, na.rm = TRUE),
    max      = max(var_data, na.rm = TRUE),
    mean     = mean(var_data, na.rm = TRUE),
    median   = median(var_data, na.rm = TRUE),
    sd       = sd(var_data, na.rm = TRUE)
  )
}

# Normalization helpers
normalize <- function(x, scale_min = NULL, scale_max = NULL) {
  if (is.null(scale_min)) scale_min <- min(x, na.rm = TRUE)
  if (is.null(scale_max)) scale_max <- max(x, na.rm = TRUE)
  (x - scale_min) / (scale_max - scale_min)
}
min_max_scale <- function(x) (x - min(x, na.rm = TRUE)) / (max(x, na.rm = TRUE) - min(x, na.rm = TRUE))
reverse_scale_min_max <- function(x) { min_val <- min(x, na.rm = TRUE); max_val <- max(x, na.rm = TRUE); max_val + min_val - x }
reverse_scale <- function(x, scale_min = NULL, scale_max = NULL) {
  if (is.null(scale_min)) scale_min <- min(x, na.rm = TRUE)
  if (is.null(scale_max)) scale_max <- max(x, na.rm = TRUE)
  scale_max + scale_min - x
}

# ID helpers
count_unique_participants <- function(df) length(unique(df$Participant_ID))
get_sub_df <- function(df, participant_id) {
  sub_df <- df[df$Participant_ID == participant_id, ]
  if (nrow(sub_df) == 0) warning("No rows found for the given Participant ID.")
  return(sub_df)
}

# Per-participant data count summary
participant_data_summary <- function(df) {
  df %>%
    count(Participant_ID, name = "n_data_points") %>%
    summarise(
      Mean   = mean(n_data_points),
      Median = median(n_data_points),
      SD     = sd(n_data_points)
    ) %>%
    print()
}

# Remove outliers based on z-score threshold for a given numeric column
remove_outliers_zscore <- function(df, column = "Mean_All_Objectives", threshold = 3) {
  # Ensure the column exists
  if (!column %in% names(df)) stop(paste("Column", column, "not found in data frame"))
  
  # Compute mean and standard deviation safely
  col_data <- df[[column]]
  mean_val <- mean(col_data, na.rm = TRUE)
  sd_val <- sd(col_data, na.rm = TRUE)
  
  # Compute z-scores
  z_scores <- (col_data - mean_val) / sd_val
  
  # Identify non-outlier rows
  keep_rows <- abs(z_scores) <= threshold
  outliers <- df[!keep_rows, ]
  cleaned_df <- df[keep_rows, ]
  
  # Print result
  cat("Outliers removed based on z-score >", threshold, ":", nrow(outliers), "\n")
  
  # Return both
  return(list(cleaned = cleaned_df, outliers = outliers))
}


# Basic demographics summary (age mean/sd and gender distribution)
get_demographics_summary <- function(df) {
  # Get one row per participant
  participant_df <- df %>%
    select(Participant_ID, Age, Gender) %>%
    distinct()
  
  # Summary statistics
  age_mean <- mean(participant_df$Age, na.rm = TRUE)
  age_sd   <- sd(participant_df$Age, na.rm = TRUE)
  
  gender_dist <- participant_df %>%
    count(Gender) %>%
    mutate(Percent = round(100 * n / sum(n), 1))
  
  cat("Age Mean:", round(age_mean, 2), "\n")
  cat("Age SD:", round(age_sd, 2), "\n")
  cat("Gender Distribution:\n")
  print(gender_dist)
}


################################################################################
################################################################################
################################################################################
################################################################################



# ---- Load & Prepare Data ----
# Set working directory to this script's location
setwd(dirname(getActiveDocumentContext()$path))

# Load pilot data and main data
main_df <- read_csv("data/main_data.csv") %>% as.data.frame()
pilot_df <- read_csv("data/pilot_data.csv") %>% as.data.frame()

# Relabel Interventions Types
main_df$Intervention_Type <- factor(
  main_df$Intervention_Type,
  levels = c("Pop-Up", "SpotOverlay", "Vibration"),
  labels = c("Baseline Intervention", "Visual Intervention", "Haptic Intervention")
)
pilot_df$Intervention_Type <- factor(
  pilot_df$Intervention_Type,
  levels = c("Pop-Up", "SpotOverlay", "Vibration"),
  labels = c("Baseline Intervention", "Visual Intervention", "Haptic Intervention")
)

# Check number of participants
cat("Number of unique participants (Pilot Data):", count_unique_participants(pilot_df), "\n")
cat("Number of unique participants (Main Data):", count_unique_participants(main_df), "\n")




# Prepare data
main_df <- main_df %>%
  mutate(
    across(
      c(Reactance, Age, FoMo, Impulsivity, Responsiveness, Goal_Alignment, Stress,
        Usefulness, Satisfaction, Agency, `Self-Control`, Sleepiness,
        Current_Activity, Anxiety, Valence
      ), as.numeric
    ),
    Participant_Number = as.numeric(factor(Participant_ID)),
    across(c(At_Home, Multitasking, Social_Situation, Intervention_Type, Gender), as.factor)
  )
pilot_df <- pilot_df %>%
  mutate(
    across(
      c(Reactance, Age, FoMo, Impulsivity, Responsiveness, Goal_Alignment, Stress,
        Usefulness, Satisfaction, Agency, `Self-Control`, Sleepiness,
        Current_Activity, Anxiety, Valence
      ), as.numeric
    ),
    Participant_Number = as.numeric(factor(Participant_ID)),
    across(c(At_Home, Multitasking, Social_Situation, Intervention_Type, Gender), as.factor)
  )


# Keep only participants who experienced all 3 interventions
main_df <- main_df %>%
  group_by(Participant_ID) %>%
  filter(n_distinct(Intervention_Type) == 3) %>%
  ungroup()


# Demographics summary
get_demographics_summary(main_df)


# Remove outliers based on z-method
result_main <- remove_outliers_zscore(main_df, column = "Responsiveness", threshold = 3)
main_df <- result_main$cleaned
result_pilot <- remove_outliers_zscore(pilot_df, column = "Responsiveness", threshold = 3)
pilot_df <- result_pilot$cleaned

# ---- Transform and Normalize ----
# Create normalized scales and objective metrics; adjust scale ranges as per your instrument
main_df <- main_df %>%
  mutate(
    # Transform and normalize
    LogTrans_Responsiveness = log1p(Responsiveness),
    
    Norm_Responsiveness     = min_max_scale(LogTrans_Responsiveness),
    Norm_Reactance     = normalize(Reactance,     scale_min = 1, scale_max = 5),
    Norm_GoalAlignment = normalize(Goal_Alignment, scale_min = 1, scale_max = 7),
    Norm_Usefulness   = normalize(Usefulness,    scale_min = 1, scale_max = 7),
    Norm_Satisfaction  = normalize(Satisfaction,  scale_min = 1, scale_max = 7),
    Norm_Agency        = normalize(Agency,        scale_min = 1, scale_max = 7),
    
    # Objective direction (higher = better)
    Objective_Responsiveness = reverse_scale_min_max(Norm_Responsiveness),
    Objective_Reactance      = reverse_scale_min_max(Norm_Reactance),
    Objective_GoalAlignment  = Norm_GoalAlignment,
    Objective_Usefulness    = Norm_Usefulness,
    Objective_Satisfaction   = Norm_Satisfaction,
    Objective_Agency         = Norm_Agency,
    
    # Normalize to specified ranges
    SelfControl  = normalize(`Self-Control`, scale_min = 1, scale_max = 5),
    Sleepiness   = normalize(Sleepiness,     scale_min = 1, scale_max = 9),
    Stress       = normalize(Stress),
    Current_Activity = normalize(Current_Activity), 
    FoMo         = normalize(FoMo,          scale_min = 1, scale_max = 5),
    Anxiety      = normalize(Anxiety,       scale_min = 0, scale_max = 3),
    Impulsivity  = normalize(Impulsivity,   scale_min = 1, scale_max = 4),
    Valence      = normalize(Valence,       scale_min = 1, scale_max = 5),
    Age          = normalize(Age)
  )

pilot_df <- pilot_df %>%
  mutate(
    # Transform and normalize
    LogTrans_Responsiveness = log1p(Responsiveness),
    
    Norm_Responsiveness     = min_max_scale(LogTrans_Responsiveness),
    Norm_Reactance     = normalize(Reactance,     scale_min = 1, scale_max = 5),
    Norm_GoalAlignment = normalize(Goal_Alignment, scale_min = 1, scale_max = 7),
    Norm_Usefulness   = normalize(Usefulness,    scale_min = 1, scale_max = 7),
    Norm_Satisfaction  = normalize(Satisfaction,  scale_min = 1, scale_max = 7),
    Norm_Agency        = normalize(Agency,        scale_min = 1, scale_max = 7),
    
    # Objective direction (higher = better)
    Objective_Responsiveness = reverse_scale_min_max(Norm_Responsiveness),
    Objective_Reactance      = reverse_scale_min_max(Norm_Reactance),
    Objective_GoalAlignment  = Norm_GoalAlignment,
    Objective_Usefulness    = Norm_Usefulness,
    Objective_Satisfaction   = Norm_Satisfaction,
    Objective_Agency         = Norm_Agency,
    
    # Normalize to specified ranges
    SelfControl  = normalize(`Self-Control`, scale_min = 1, scale_max = 5),
    Sleepiness   = normalize(Sleepiness,     scale_min = 1, scale_max = 9),
    Stress       = normalize(Stress),
    Current_Activity = normalize(Current_Activity), 
    FoMo         = normalize(FoMo,          scale_min = 1, scale_max = 5),
    Anxiety      = normalize(Anxiety,       scale_min = 0, scale_max = 3),
    Impulsivity  = normalize(Impulsivity,   scale_min = 1, scale_max = 4),
    Valence      = normalize(Valence,       scale_min = 1, scale_max = 5),
    Age          = normalize(Age)
  )

# ---- Compute Effectiveness Scores ----
combined_measures <- c("Objective_Responsiveness", "Objective_Reactance", "Objective_GoalAlignment", "Objective_Satisfaction", "Objective_Agency", "Objective_Usefulness")
objective_measures <- c("Objective_Responsiveness")
subjective_measures <- c("Objective_Reactance", "Objective_GoalAlignment", "Objective_Satisfaction", "Objective_Agency", "Objective_Usefulness")

pilot_df$combined_effectiveness <- rowMeans(pilot_df[combined_measures], na.rm = TRUE)
main_df$Objective_Effectiveness <- rowMeans(main_df[objective_measures], na.rm = TRUE)
main_df$Subjective_Effectiveness <- rowMeans(main_df[subjective_measures], na.rm = TRUE)



################################################################################
################################################################################
################################################################################
################################################################################

#  ------------- Mixed Model & Power Simulation using Plot Data ----------------

model_full_pilot <- lmer(
  combined_effectiveness ~ Intervention_Type * 
    (Valence + Impulsivity + Sleepiness + FoMo + Stress + Multitasking + At_Home + SelfControl +
       Anxiety + Current_Activity + Social_Situation) +
    (1 | Participant_Number),
  data = pilot_df
)
summary(model_full_pilot)

model_sim <- lmer(
  combined_effectiveness ~ Intervention_Type * 
    (Anxiety + Impulsivity + FoMo) + (1 | Participant_Number),
  data = pilot_df
)

power_results_mixed <- mixedpower(
  model        = model_sim,
  data         = main_df,
  fixed_effects = c("Intervention_Type", "Anxiety", "Impulsivity", "FoMo"),
  simvar       = "Participant_Number",
  steps        = c(30, 60, 90, 120, 150),
  critical_value = 2,
  n_sim        = 2
)

################################################################################
################################################################################
################################################################################
################################################################################

#  ---------------------------- Survival Analysis  -----------------------------


# Max duration of the intervention (3 min 31 s = 211s)
cutoff <- 211  

main_df <- main_df %>%
  mutate(
    event = ifelse(Responsiveness < cutoff, 1, 0),
    time  = pmin(Responsiveness, cutoff)  # survival time is capped at cutoff
  )



# ---- Survival object ----
surv_obj <- with(main_df, Surv(time, event))




# ---- Kaplan–Meier curves (descriptive only) ----
# Relabel factor levels


fit_km <- survfit(surv_obj ~ Intervention_Type, data = main_df)

# Kaplan-Meier plot
km_plot <- ggsurvplot(
  fit_km,
  data = main_df,
  conf.int = TRUE,
  pval = TRUE,                 # log-rank test (ignores clustering)
  risk.table = TRUE,
  palette = c("skyblue", "#ff7f0e", "#EB4F95"),
  xlab = "Time to stop scrolling (s)",
  ylab = "Probability of continuing scrolling",
  legend.title = NULL          # remove legend title
)
km_plot


# ---- Cox model with participant clustering ----
cox_clust <- coxph(
  surv_obj ~ Intervention_Type + cluster(Participant_Number),
  data = main_df,
  ties = "efron"
)
summary(cox_clust)

# ---- Pairwise comparisons (log-HR scale) ----
# emmeans on the clustered Cox model
emm  <- emmeans(cox_clust, ~ Intervention_Type, vcov. = cox_clust$var)
pairs(emm, adjust = "tukey")
summary(emm, infer = TRUE)


################################################################################
################################################################################
################################################################################
################################################################################

#  -------------- Bayesian Mixed Effect Model on the Main Data  ----------------


prior <- c(
  set_prior("normal(0, 1)", class = "b")
)

# Shapiro Wilk Test (Normality test)
shapiro.test(main_df$Objective_Effectiveness)

# Model for objective effectiveness
model_bayes_objective <- brm(
  formula = Objective_Effectiveness ~ Intervention_Type * (Valence + Impulsivity + Sleepiness + FoMo + Stress + Multitasking + At_Home + SelfControl + Anxiety + Current_Activity + Social_Situation) + (1 + Intervention_Type + Current_Activity + Social_Situation + Multitasking + At_Home + Stress + Sleepiness + Valence | Participant_Number),
  data = main_df,
  prior = prior, 
  cores = 15,
  chains = 4,   
  family = student(),
  iter = 11000,        # total iterations per chain
  warmup = 1000,       # burn-in period
)

report <- describe_posterior(model_bayes_objective, test = c("p_direction", "rope", "bayesfactor"))
report
r2_bayes(model_bayes_objective)



# Shapiro Wilk Test (Normality test)
shapiro.test(main_df$Subjective_Effectiveness)

# Model for subjective effectiveness
model_bayes_subjective <- brm(
  formula = Subjective_Effectiveness ~ Intervention_Type * (Valence + Impulsivity + Sleepiness + FoMo + Stress + Multitasking + At_Home + SelfControl + Anxiety + Current_Activity + Social_Situation) + (1 + Intervention_Type + Current_Activity + Social_Situation + Multitasking + At_Home + Stress + Sleepiness + Valence | Participant_Number),
  data = main_df,
  prior = prior, 
  cores = 15,
  chains = 4,   
  family = student(),
  iter = 11000,        # total iterations per chain
  warmup = 1000,       # burn-in period
)

report <- describe_posterior(model_bayes_subjective, test = c("p_direction", "rope", "bayesfactor"))
report
r2_bayes(model_bayes_subjective)

