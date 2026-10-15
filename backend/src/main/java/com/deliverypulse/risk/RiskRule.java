package com.deliverypulse.risk;

import java.util.List;

public interface RiskRule {

    List<RiskFinding> evaluate(AnalysisContext context);
}