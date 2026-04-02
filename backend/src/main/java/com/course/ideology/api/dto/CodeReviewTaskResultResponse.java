package com.course.ideology.api.dto;

import java.util.ArrayList;
import java.util.List;

public class CodeReviewTaskResultResponse {
    private String projectStructure;
    private String issueList;
    private String riskLevel;
    private String fixSuggestions;
    private String summary;

    private boolean fallbackResult;
    private String fallbackReason;
    private String resultSource;

    private ReviewTask reviewTask;
    private ReviewSummary reviewSummary;
    private List<ReviewIssue> reviewIssue = new ArrayList<>();
    private List<DirectoryStat> directoryStats = new ArrayList<>();
    private List<FileRiskStat> fileRiskStats = new ArrayList<>();
    private HealthScore healthScore;
    private CoverageStats coverageStats;
    private List<LanguageStat> languageStats = new ArrayList<>();
    private List<RepairBenefitStat> repairBenefitStats = new ArrayList<>();
    private List<TimelineLog> timelineLogs = new ArrayList<>();

    public CodeReviewTaskResultResponse() {
    }

    public CodeReviewTaskResultResponse(String projectStructure,
                                        String issueList,
                                        String riskLevel,
                                        String fixSuggestions,
                                        String summary) {
        this.projectStructure = projectStructure;
        this.issueList = issueList;
        this.riskLevel = riskLevel;
        this.fixSuggestions = fixSuggestions;
        this.summary = summary;
    }

    public String getProjectStructure() {
        return projectStructure;
    }

    public void setProjectStructure(String projectStructure) {
        this.projectStructure = projectStructure;
    }

    public String getIssueList() {
        return issueList;
    }

    public void setIssueList(String issueList) {
        this.issueList = issueList;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getFixSuggestions() {
        return fixSuggestions;
    }

    public void setFixSuggestions(String fixSuggestions) {
        this.fixSuggestions = fixSuggestions;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public boolean isFallbackResult() {
        return fallbackResult;
    }

    public void setFallbackResult(boolean fallbackResult) {
        this.fallbackResult = fallbackResult;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason;
    }

    public String getResultSource() {
        return resultSource;
    }

    public void setResultSource(String resultSource) {
        this.resultSource = resultSource;
    }

    public ReviewTask getReviewTask() {
        return reviewTask;
    }

    public void setReviewTask(ReviewTask reviewTask) {
        this.reviewTask = reviewTask;
    }

    public ReviewSummary getReviewSummary() {
        return reviewSummary;
    }

    public void setReviewSummary(ReviewSummary reviewSummary) {
        this.reviewSummary = reviewSummary;
    }

    public List<ReviewIssue> getReviewIssue() {
        return reviewIssue;
    }

    public void setReviewIssue(List<ReviewIssue> reviewIssue) {
        this.reviewIssue = reviewIssue == null ? new ArrayList<>() : new ArrayList<>(reviewIssue);
    }

    public List<DirectoryStat> getDirectoryStats() {
        return directoryStats;
    }

    public void setDirectoryStats(List<DirectoryStat> directoryStats) {
        this.directoryStats = directoryStats == null ? new ArrayList<>() : new ArrayList<>(directoryStats);
    }

    public List<FileRiskStat> getFileRiskStats() {
        return fileRiskStats;
    }

    public void setFileRiskStats(List<FileRiskStat> fileRiskStats) {
        this.fileRiskStats = fileRiskStats == null ? new ArrayList<>() : new ArrayList<>(fileRiskStats);
    }

    public HealthScore getHealthScore() {
        return healthScore;
    }

    public void setHealthScore(HealthScore healthScore) {
        this.healthScore = healthScore;
    }

    public CoverageStats getCoverageStats() {
        return coverageStats;
    }

    public void setCoverageStats(CoverageStats coverageStats) {
        this.coverageStats = coverageStats;
    }

    public List<LanguageStat> getLanguageStats() {
        return languageStats;
    }

    public void setLanguageStats(List<LanguageStat> languageStats) {
        this.languageStats = languageStats == null ? new ArrayList<>() : new ArrayList<>(languageStats);
    }

    public List<RepairBenefitStat> getRepairBenefitStats() {
        return repairBenefitStats;
    }

    public void setRepairBenefitStats(List<RepairBenefitStat> repairBenefitStats) {
        this.repairBenefitStats = repairBenefitStats == null ? new ArrayList<>() : new ArrayList<>(repairBenefitStats);
    }

    public List<TimelineLog> getTimelineLogs() {
        return timelineLogs;
    }

    public void setTimelineLogs(List<TimelineLog> timelineLogs) {
        this.timelineLogs = timelineLogs == null ? new ArrayList<>() : new ArrayList<>(timelineLogs);
    }

    public static class ReviewTask {
        private String taskId;
        private String repoUrl;
        private String projectPath;
        private String model;
        private String status;
        private String startedAt;
        private String finishedAt;
        private String reviewInstruction;

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getRepoUrl() {
            return repoUrl;
        }

        public void setRepoUrl(String repoUrl) {
            this.repoUrl = repoUrl;
        }

        public String getProjectPath() {
            return projectPath;
        }

        public void setProjectPath(String projectPath) {
            this.projectPath = projectPath;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getStartedAt() {
            return startedAt;
        }

        public void setStartedAt(String startedAt) {
            this.startedAt = startedAt;
        }

        public String getFinishedAt() {
            return finishedAt;
        }

        public void setFinishedAt(String finishedAt) {
            this.finishedAt = finishedAt;
        }

        public String getReviewInstruction() {
            return reviewInstruction;
        }

        public void setReviewInstruction(String reviewInstruction) {
            this.reviewInstruction = reviewInstruction;
        }
    }

    public static class ReviewSummary {
        private String riskLevel;
        private int issueCount;
        private String conclusion;
        private String source;
        private boolean fallbackResult;
        private String fallbackReason;

        public String getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
        }

        public int getIssueCount() {
            return issueCount;
        }

        public void setIssueCount(int issueCount) {
            this.issueCount = issueCount;
        }

        public String getConclusion() {
            return conclusion;
        }

        public void setConclusion(String conclusion) {
            this.conclusion = conclusion;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public boolean isFallbackResult() {
            return fallbackResult;
        }

        public void setFallbackResult(boolean fallbackResult) {
            this.fallbackResult = fallbackResult;
        }

        public String getFallbackReason() {
            return fallbackReason;
        }

        public void setFallbackReason(String fallbackReason) {
            this.fallbackReason = fallbackReason;
        }
    }

    public static class ReviewIssue {
        private String title;
        private String description;
        private String riskLevel;
        private String issueType;
        private String filePath;
        private String directoryPath;
        private int lineStart;
        private int lineEnd;
        private String suggestion;
        private boolean fromRealLlm;
        private String sourceNote;
        private double confidence;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
        }

        public String getIssueType() {
            return issueType;
        }

        public void setIssueType(String issueType) {
            this.issueType = issueType;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getDirectoryPath() {
            return directoryPath;
        }

        public void setDirectoryPath(String directoryPath) {
            this.directoryPath = directoryPath;
        }

        public int getLineStart() {
            return lineStart;
        }

        public void setLineStart(int lineStart) {
            this.lineStart = lineStart;
        }

        public int getLineEnd() {
            return lineEnd;
        }

        public void setLineEnd(int lineEnd) {
            this.lineEnd = lineEnd;
        }

        public String getSuggestion() {
            return suggestion;
        }

        public void setSuggestion(String suggestion) {
            this.suggestion = suggestion;
        }

        public boolean isFromRealLlm() {
            return fromRealLlm;
        }

        public void setFromRealLlm(boolean fromRealLlm) {
            this.fromRealLlm = fromRealLlm;
        }

        public String getSourceNote() {
            return sourceNote;
        }

        public void setSourceNote(String sourceNote) {
            this.sourceNote = sourceNote;
        }

        public double getConfidence() {
            return confidence;
        }

        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }
    }

    public static class DirectoryStat {
        private String directoryPath;
        private int issueCount;
        private int highCount;
        private int mediumCount;
        private int lowCount;
        private int infoCount;

        public String getDirectoryPath() {
            return directoryPath;
        }

        public void setDirectoryPath(String directoryPath) {
            this.directoryPath = directoryPath;
        }

        public int getIssueCount() {
            return issueCount;
        }

        public void setIssueCount(int issueCount) {
            this.issueCount = issueCount;
        }

        public int getHighCount() {
            return highCount;
        }

        public void setHighCount(int highCount) {
            this.highCount = highCount;
        }

        public int getMediumCount() {
            return mediumCount;
        }

        public void setMediumCount(int mediumCount) {
            this.mediumCount = mediumCount;
        }

        public int getLowCount() {
            return lowCount;
        }

        public void setLowCount(int lowCount) {
            this.lowCount = lowCount;
        }

        public int getInfoCount() {
            return infoCount;
        }

        public void setInfoCount(int infoCount) {
            this.infoCount = infoCount;
        }
    }

    public static class FileRiskStat {
        private String filePath;
        private String directoryPath;
        private int issueCount;
        private String maxRiskLevel;
        private List<String> issueTypes = new ArrayList<>();
        private double riskScore;

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getDirectoryPath() {
            return directoryPath;
        }

        public void setDirectoryPath(String directoryPath) {
            this.directoryPath = directoryPath;
        }

        public int getIssueCount() {
            return issueCount;
        }

        public void setIssueCount(int issueCount) {
            this.issueCount = issueCount;
        }

        public String getMaxRiskLevel() {
            return maxRiskLevel;
        }

        public void setMaxRiskLevel(String maxRiskLevel) {
            this.maxRiskLevel = maxRiskLevel;
        }

        public List<String> getIssueTypes() {
            return issueTypes;
        }

        public void setIssueTypes(List<String> issueTypes) {
            this.issueTypes = issueTypes == null ? new ArrayList<>() : new ArrayList<>(issueTypes);
        }

        public double getRiskScore() {
            return riskScore;
        }

        public void setRiskScore(double riskScore) {
            this.riskScore = riskScore;
        }
    }

    public static class HealthScore {
        private double overall;
        private double security;
        private double robustness;
        private double maintainability;
        private double testingCompleteness;
        private double structureClarity;

        public double getOverall() {
            return overall;
        }

        public void setOverall(double overall) {
            this.overall = overall;
        }

        public double getSecurity() {
            return security;
        }

        public void setSecurity(double security) {
            this.security = security;
        }

        public double getRobustness() {
            return robustness;
        }

        public void setRobustness(double robustness) {
            this.robustness = robustness;
        }

        public double getMaintainability() {
            return maintainability;
        }

        public void setMaintainability(double maintainability) {
            this.maintainability = maintainability;
        }

        public double getTestingCompleteness() {
            return testingCompleteness;
        }

        public void setTestingCompleteness(double testingCompleteness) {
            this.testingCompleteness = testingCompleteness;
        }

        public double getStructureClarity() {
            return structureClarity;
        }

        public void setStructureClarity(double structureClarity) {
            this.structureClarity = structureClarity;
        }
    }

    public static class CoverageStats {
        private int totalFiles;
        private int analyzedFiles;
        private int skippedFiles;
        private double coverageRate;

        public int getTotalFiles() {
            return totalFiles;
        }

        public void setTotalFiles(int totalFiles) {
            this.totalFiles = totalFiles;
        }

        public int getAnalyzedFiles() {
            return analyzedFiles;
        }

        public void setAnalyzedFiles(int analyzedFiles) {
            this.analyzedFiles = analyzedFiles;
        }

        public int getSkippedFiles() {
            return skippedFiles;
        }

        public void setSkippedFiles(int skippedFiles) {
            this.skippedFiles = skippedFiles;
        }

        public double getCoverageRate() {
            return coverageRate;
        }

        public void setCoverageRate(double coverageRate) {
            this.coverageRate = coverageRate;
        }
    }

    public static class LanguageStat {
        private String language;
        private int fileCount;
        private double percentage;

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public int getFileCount() {
            return fileCount;
        }

        public void setFileCount(int fileCount) {
            this.fileCount = fileCount;
        }

        public double getPercentage() {
            return percentage;
        }

        public void setPercentage(double percentage) {
            this.percentage = percentage;
        }
    }

    public static class RepairBenefitStat {
        private String filePath;
        private String issueType;
        private double riskScore;
        private double repairCost;
        private double benefitScore;
        private String benefitLevel;
        private String recommendation;

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getIssueType() {
            return issueType;
        }

        public void setIssueType(String issueType) {
            this.issueType = issueType;
        }

        public double getRiskScore() {
            return riskScore;
        }

        public void setRiskScore(double riskScore) {
            this.riskScore = riskScore;
        }

        public double getRepairCost() {
            return repairCost;
        }

        public void setRepairCost(double repairCost) {
            this.repairCost = repairCost;
        }

        public double getBenefitScore() {
            return benefitScore;
        }

        public void setBenefitScore(double benefitScore) {
            this.benefitScore = benefitScore;
        }

        public String getBenefitLevel() {
            return benefitLevel;
        }

        public void setBenefitLevel(String benefitLevel) {
            this.benefitLevel = benefitLevel;
        }

        public String getRecommendation() {
            return recommendation;
        }

        public void setRecommendation(String recommendation) {
            this.recommendation = recommendation;
        }
    }

    public static class TimelineLog {
        private String stage;
        private String status;
        private String message;
        private int progress;
        private String timestamp;

        public String getStage() {
            return stage;
        }

        public void setStage(String stage) {
            this.stage = stage;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public int getProgress() {
            return progress;
        }

        public void setProgress(int progress) {
            this.progress = progress;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
    }
}
