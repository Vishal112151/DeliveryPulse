package com.deliverypulse.auth;

/**
 * ADMIN sees every project and manages users, Jira sync and jobs. MANAGER sees assigned projects and can
 * upload, analyze and generate insights. VIEWER sees assigned projects read-only.
 */
public enum Role {
    ADMIN,
    MANAGER,
    VIEWER
}
