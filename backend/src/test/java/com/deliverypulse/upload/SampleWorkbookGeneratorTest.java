package com.deliverypulse.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SampleWorkbookGeneratorTest {

    @Test
    void generatesTheFourProjectTwentyFiveTaskDemoWorkbook() throws Exception {
        Path sampleDirectory = Path.of("target", "sample-data");
        Files.createDirectories(sampleDirectory);
        Path workbook = sampleDirectory.resolve("deliverypulse-portfolio.xlsx");
        Files.write(workbook, ImportTestFiles.portfolioWorkbook());

        assertThat(Files.size(workbook)).isPositive();
    }
}