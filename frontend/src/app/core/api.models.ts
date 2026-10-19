export type TaskStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'BLOCKED' | 'DONE';
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type RiskSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type RiskCategory =
  | 'OVERDUE_TASK'
  | 'BLOCKED_TASK'
  | 'CRITICAL_TASK_AT_RISK'
  | 'DEPENDENCY_RISK'
  | 'DELIVERY_RISK'
  | 'DATA_QUALITY_WARNING'
  | 'DEFECT_RISK'
  | 'RECURRING_BLOCKER';
export type IssueType = 'TASK' | 'DEFECT';
export type MilestoneStatus = 'COMPLETE' | 'MISSED' | 'AT_RISK' | 'ON_TRACK' | 'NO_DATE';
export type RiskCertainty = 'CONFIRMED' | 'POTENTIAL' | 'DATA_QUALITY';
export type RiskStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';
export type TaskSortField = 'TASK_ID' | 'TASK_NAME' | 'STATUS' | 'PRIORITY' | 'ASSIGNEE' | 'DUE_DATE';
export type TaskSortDirection = 'ASC' | 'DESC';

export interface ProjectResponse {
  id: string;
  name: string;
  sourceSheetName: string | null;
  description: string | null;
  createdAt: string;
  updatedAt: string;
  analysisDate: string | null;
  analysisTimestamp: string | null;
  taskCount: number;
  completedTaskCount: number;
  incompleteTaskCount: number;
  overdueTaskCount: number;
  blockedTaskCount: number;
  risksBySeverity: Record<RiskSeverity, number>;
  openDefectCount: number;
}

export interface TaskResponse {
  id: string;
  projectId: string;
  taskId: string;
  taskName: string;
  teamOrModule: string | null;
  assignee: string | null;
  priority: Priority;
  status: TaskStatus;
  startDate: string | null;
  dueDate: string | null;
  overdue: boolean;
  blockerText: string | null;
  description: string | null;
  issueType: IssueType;
  milestone: string | null;
  milestoneDate: string | null;
}

export interface TaskPageResponse {
  content: TaskResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  analysisDate: string;
  analysisTimestamp: string;
  sortBy: TaskSortField;
  sortDirection: TaskSortDirection;
}

export interface RiskResponse {
  id: string;
  category: RiskCategory;
  severity: RiskSeverity;
  certainty: RiskCertainty;
  status: RiskStatus;
  title: string;
  explanation: string;
  evidence: string | null;
  supportingTaskIds: string[];
  recommendedAction: string | null;
  detectedAt: string;
}

export interface ProjectRiskSummary {
  projectId: string;
  projectName: string;
  analysisDate: string;
  analysisTimestamp: string | null;
  taskCount: number;
  completedTaskCount: number;
  incompleteTaskCount: number;
  overdueTaskCount: number;
  blockedTaskCount: number;
  activeRiskCount: number;
  risksBySeverity: Record<RiskSeverity, number>;
  risksByCategory: Record<RiskCategory, number>;
  risks: RiskResponse[];
  openDefectCount: number;
  milestones: MilestoneSummary[];
}

export interface MilestoneSummary {
  name: string;
  milestoneDate: string | null;
  taskCount: number;
  completedTaskCount: number;
  blockedTaskCount: number;
  openDefectCount: number;
  status: MilestoneStatus;
}

export interface PortfolioRiskSummary {
  analysisDate: string;
  analysisTimestamp: string;
  projectCount: number;
  taskCount: number;
  completedTaskCount: number;
  incompleteTaskCount: number;
  overdueTaskCount: number;
  blockedTaskCount: number;
  activeRiskCount: number;
  risksBySeverity: Record<RiskSeverity, number>;
  risksByCategory: Record<RiskCategory, number>;
  projects: ProjectRiskSummary[];
  openDefectCount: number;
}

/** Task status changes recorded by imports during the report week. */
export interface WeeklyActivity {
  tasksAdded: number;
  tasksCompleted: number;
  tasksBlocked: number;
  tasksUnblocked: number;
  defectsOpened: number;
  defectsClosed: number;
}

export interface WeeklyReportResponse extends PortfolioRiskSummary {
  weekStartDate: string;
  weekEndDate: string;
  activity: WeeklyActivity;
  activityByProject: Record<string, WeeklyActivity>;
}

export interface ImportIssue {
  worksheet: string;
  row: number | null;
  column: string | null;
  code: string;
  message: string;
}

export interface ImportSummary {
  projectCount: number;
  importedTaskCount: number;
  importedProjects: string[];
  skippedWorksheets: string[];
  warnings: ImportIssue[];
  errors: ImportIssue[];
}

export interface JiraStatusResponse {
  configured: boolean;
  baseUrl: string | null;
  projectKeys: string[];
  blockedLabel: string;
}

export interface JobRun {
  startedAt: string;
  finishedAt: string;
  manual: boolean;
  succeeded: boolean;
  message: string;
}

export interface JobStatus {
  name: 'refresh' | 'weekly-report';
  description: string;
  cron: string;
  scheduled: boolean;
  nextRunAt: string | null;
  running: boolean;
  lastRun: JobRun | null;
}

export interface ReportSnapshotSummary {
  id: string;
  weekStartDate: string;
  weekEndDate: string;
  generatedAt: string;
  trigger: 'SCHEDULED' | 'MANUAL';
}

export interface TaskQuery {
  page: number;
  size: number;
  status?: TaskStatus;
  priority?: Priority;
  assignee?: string;
  overdue?: boolean;
  analysisDate?: string;
  sortBy: TaskSortField;
  sortDirection: TaskSortDirection;
}

export interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
  errors?: Record<string, string>;
}
export type StatementBasis = 'FACT' | 'ASSUMPTION';
export type InsightSource = 'AI' | 'DETERMINISTIC_FALLBACK';
export type InsightStatus =
  | 'GENERATED'
  | 'DISABLED'
  | 'NOT_CONFIGURED'
  | 'NOT_AUTHORIZED'
  | 'ANALYSIS_REQUIRED'
  | 'TIMEOUT'
  | 'RATE_LIMITED'
  | 'QUOTA_EXCEEDED'
  | 'AUTHENTICATION_FAILED'
  | 'UNAVAILABLE'
  | 'REFUSED'
  | 'INVALID_RESPONSE';

export interface InsightStatement {
  text: string;
  basis: StatementBasis;
  supportingTaskIds: string[];
}

export interface RiskInsight {
  riskId: string;
  title: string;
  severity: RiskSeverity;
  category: RiskCategory;
  certainty: RiskCertainty;
  supportingTaskIds: string[];
  explanation: InsightStatement[];
}

export interface ActionInsight {
  action: string;
  basis: StatementBasis;
  supportingTaskIds: string[];
  relatedRiskIds: string[];
}

export interface WeeklyStatusInsight {
  summary: InsightStatement[];
  progress: InsightStatement[];
  risksAndIssues: InsightStatement[];
  nextSteps: InsightStatement[];
}

export interface ProjectInsightsResponse {
  /** null for the portfolio briefing. */
  projectId: string | null;
  projectName: string;
  source: InsightSource;
  status: InsightStatus;
  message: string;
  model: string | null;
  generatedAt: string;
  analysisDate: string | null;
  executiveSummary: InsightStatement[];
  riskExplanations: RiskInsight[];
  recommendedActions: ActionInsight[];
  weeklyStatus: WeeklyStatusInsight;
  validationWarnings: string[];
  disclaimer: string;
}

export interface AiStatusResponse {
  enabled: boolean;
  configured: boolean;
  externalDataSharingAuthorized: boolean;
  available: boolean;
  provider: string;
  model: string;
  status: InsightStatus;
  message: string;
}
