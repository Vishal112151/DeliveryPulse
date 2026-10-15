package com.deliverypulse.upload;

public record ImportIssue(
        String worksheet,
        Integer row,
        String column,
        String code,
        String message) {
}