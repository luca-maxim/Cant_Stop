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
count_unique_participants <- function(df) length(unique(df$Prolific_ID))
get_sub_df <- function(df, prolific_id) {
  sub_df <- df[df$Prolific_ID == prolific_id, ]
  if (nrow(sub_df) == 0) warning("No rows found for the given Prolific_ID.")
  return(sub_df)
}

# Per-participant data count summary
participant_data_summary <- function(df) {
  df %>%
    count(Prolific_ID, name = "n_data_points") %>%
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
    select(Prolific_ID, Age, Gender) %>%
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


# ---- Load & Prepare Data ----
# Set working directory to this script's location
setwd(dirname(getActiveDocumentContext()$path))

# Load data
main_df <- read_csv("data/data.csv") %>% as.data.frame()

# Prepare data
main_df <- main_df %>%
  mutate(
    across(
      c(
        Reactance, Age, FoMo, Impulsivity, Responsiveness, Goal_Alignment, Stress,
        Usefulness, Satisfaction, Agency, `Self-Control`, Sleepiness,
        Current_Activity, Anxiety, Valence
      ),
      as.numeric
    ),
    Participant_Number = as.numeric(factor(Prolific_ID)),
    across(c(At_Home, Multitasking, Social_Situation, Intervention_Type, Gender), as.factor)
  )
cat("Number of unique participants (raw):", count_unique_participants(main_df), "\n")

# Keep only participants who experienced all 3 interventions
main_df <- main_df %>%
  group_by(Prolific_ID) %>%
  filter(n_distinct(Intervention_Type) == 3) %>%
  ungroup()

# Sort by timestamp
main_df <- main_df %>% arrange(Timestamp)

# Helper: select first N unique participants
select_n_participants <- function(df, n = 10) {
  selected_ids <- df %>%
    distinct(Prolific_ID) %>%
    slice_head(n = n) %>%
    pull(Prolific_ID)
  df %>% filter(Prolific_ID %in% selected_ids)
}
main_df <- select_n_participants(main_df, n = 300)
cat("Number of unique participants (after selection):", count_unique_participants(main_df), "\n")

# Demographics summary (text output)
get_demographics_summary(main_df)

# Remove outliers based on Responsiveness
result <- remove_outliers_zscore(main_df, column = "Responsiveness", threshold = 3)
main_df <- result$cleaned
outliers_df <- result$outliers
cat("Number of unique participants (after outlier removal):", count_unique_participants(main_df), "\n")
participant_data_summary(main_df)

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

# ---- Overall Objective Score ----
objectives <- c(
  "Objective_Responsiveness",
  "Objective_Reactance",
  "Objective_GoalAlignment",
  "Objective_Satisfaction",
  "Objective_Agency",
  "Objective_Usefulness"
)
main_df$Mean_All_Objectives <- rowMeans(main_df[objectives], na.rm = TRUE)

# Persist the cleaned/derived dataset
write.csv(main_df, "intervention_cleanData_Albin.csv", row.names = FALSE)

# Shapiro Wilk Test (Normality test)
shapiro.test(main_df$Mean_All_Objectives)


#  ---- Mixed Model & Power Simulation ----
model_full <- lmer(
  Mean_All_Objectives ~ Intervention_Type * 
    (Valence + Impulsivity + Sleepiness + FoMo + Stress + Multitasking + At_Home + SelfControl +
       Anxiety + Current_Activity + Social_Situation) +
    (1 | Participant_Number),
  data = main_df
)
summary(model_full)

prior <- c(
  set_prior("normal(0, 1)", class = "b")
)

model_bayes <- brm(
  formula = Mean_All_Objectives ~ 0 + Intervention_Type * (Valence + Impulsivity + Sleepiness + FoMo + Stress + Multitasking + At_Home + SelfControl + Anxiety + Current_Activity + Social_Situation) + (1 + Intervention_Type + Current_Activity + Social_Situation + Multitasking + At_Home + Stress + Sleepiness + Valence | Participant_Number),
  data = main_df,
  prior = prior, 
  cores = 15,
  chains = 4,   
  family = student(),
  iter = 11000,        # total iterations per chain
  warmup = 1000,       # burn-in period
)

report <- describe_posterior(model_bayes, test = c("p_direction", "rope", "bayesfactor"))
report

model_bayes$fit

r <- report(model_bayes, verbose = TRUE)
residuals(model_bayes)

summary(model_bayes, pars = "b:Intervention_Type") ## only interaction terms with intervention type

conditional_effects(model_bayes)
pp_check(model_bayes)
plot(model_bayes)

pd_results <- pd(model_bayes)
print(pd_results)
prior_summary(model_bayes)
posterior_summary(model_bayes)
bayes_R2(model_bayes, probs = c(0.025, 0.975))


# ---- LaTeX Table Export ----
df <- report
colnames(df)

# 1. Prepare and clean numeric columns
df$pd <- as.numeric(gsub("%", "", df$pd))
df$`% in ROPE` <- df$ROPE_Percentage
df$BF <- round(exp(df$log_BF), 2)
df$ESS <- round(df$ESS)

# 2. Create CI in [low, high] format
df$`95% CI` <- sprintf("[%.2f, %.2f]", df$CI_low, df$CI_high)

# 3. Filter based on significance
df_filtered <- subset(df, pd > 95 | `% in ROPE` < 5)

# 4. Select and rename relevant columns
df_filtered <- df_filtered[, c("Parameter", "Median", "95% CI", "pd", "% in ROPE", "BF", "ESS")]
colnames(df_filtered) <- c("Parameter", "Median", "95\\% CI", "pd", "\\% in ROPE", "BF", "ESS")

# 5. Format percentage columns
df_filtered$pd <- sprintf("%.2f\\%%", df_filtered$pd)
df_filtered$`\\% in ROPE` <- sprintf("%.2f\\%%", df_filtered$`\\% in ROPE`)

# 6. Print LaTeX table
print(
  xtable(df_filtered, caption = "Table", align = "lccccccc", digits = 2),
  include.rownames = FALSE,
  sanitize.text.function = identity,
  floating.environment = "table",
  tabular.environment = "tabular",
  hline.after = c(-1, 0, nrow(df_filtered)),
  add.to.row = list(pos = list(0), command = "\\toprule\n"),
  comment = FALSE
)

model_bayes$fit@stan_args[[1]]$algorithm

rope_value <- rope_range(model_bayes)
rope_value

# Define pretty names for interactions
rename_lookup <- c(
  "b_Intervention_TypeSpotOverlay:Anxiety"      = "Visual Inter. × Anxiety",
  "b_Intervention_TypeSpotOverlay:Impulsivity"  = "Visual Inter. × Impulsivity",
  "b_Intervention_TypeSpotOverlay:Sleepiness"   = "Visual Inter. × Sleepiness",
  "b_Intervention_TypeVibration:SelfControl"    = "Haptic Inter. × Self-Control",
  "b_Intervention_TypeVibration:Impulsivity"    = "Haptic Inter. × Impulsivity",
  "b_Intervention_TypeVibration:FoMo"           = "Haptic Inter. × FoMo",
  "b_Intervention_TypeSpotOverlay:FoMo"         = "Visual Inter. × FoMo",
  "b_Intervention_TypeVibration:Stress"         = "Haptic Inter. × Stress",
  "b_Intervention_TypeVibration:Impulsivity"    = "Haptic Inter. × Impulsivity",
  "b_Intervention_TypeSpotOverlay:GenderMale"   = "Haptic Inter. × Gender"
)

# 2. Extract interaction draws
interaction_draws <- model_bayes %>%
  spread_draws(`b_Intervention_Type.*:.*`, regex = TRUE)

# Get % in ROPE for interaction terms
rope_filter <- describe_posterior(model_bayes, test = "rope") %>%
  filter(grepl("^b_Intervention_Type.*:.*", Parameter)) %>%
  filter(ROPE_Percentage < 0.1) %>%
  filter(Parameter %in% names(rename_lookup)) %>%
  mutate(Label = rename_lookup[Parameter])


# ---- Power Simulation ----
model_sim <- lmer(
  Mean_All_Objectives ~ Intervention_Type * 
    (Anxiety + Impulsivity + FoMo) + (1 | Participant_Number),
  data = main_df
)

power_results_mixed <- mixedpower(
  model        = model_sim,
  data         = main_df,
  fixed_effects = c("Intervention_Type", "Anxiety", "Impulsivity", "FoMo"),
  simvar       = "Participant_Number",
  steps        = c(30, 60, 90, 120, 150),
  critical_value = 2,
  n_sim        = 1000
)

sink("sim_model_power_mixed_results_50_150_30_participants.txt")
print(power_results_mixed)
sink()

# ---- Model Reporting ----
summary(model_full)
report(model)
model_performance(model_full)
logLik(model)