package com.walmartlabs.concord.project.runtime.v2.parser;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.runtime.v2.exception.YamlParserException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class YamlErrorParserTest extends AbstractParserTest {

    @Test
    public void test001() throws Exception {
        String msg = "(001.yml): Error @ line: 1, col: 10. Invalid value type, expected: IMPORTS, got: STRING\n" +
                "\twhile processing steps:\n" +
                "\t'imports' @ line: 1, col: 1";

        assertErrorMessage("errors/imports/001.yml", msg);
    }

    @Test
    public void test002() throws Exception {
        String msg = "(002.yml): Error @ line: 2, col: 3. Invalid value type, expected: IMPORTS, got: OBJECT\n" +
                "\twhile processing steps:\n" +
                "\t'imports' @ line: 1, col: 1";

        assertErrorMessage("errors/imports/002.yml", msg);
    }

    @Test
    public void test003() throws Exception {
        String msg = "(003.yml): Error @ line: 2, col: 9. Invalid value type, expected: GIT_IMPORT, got: NULL. Remove attribute or complete the definition\n" +
                "\twhile processing steps:\n" +
                "\t'git' @ line: 2, col: 5\n" +
                "\t\t'imports' @ line: 1, col: 1";

        assertErrorMessage("errors/imports/003.yml", msg);
    }

    @Test
    public void test004() throws Exception {
        String msg = "(004.yml): Error @ line: 2, col: 10. Invalid value type, expected: GIT_IMPORT, got: STRING\n" +
                "\twhile processing steps:\n" +
                "\t'git' @ line: 2, col: 5\n" +
                "\t\t'imports' @ line: 1, col: 1";

        assertErrorMessage("errors/imports/004.yml", msg);
    }

    @Test
    public void test005() throws Exception {
        String msg = "(005.yml): Error @ line: 2, col: 9. Invalid value type, expected: GIT_IMPORT, got: NULL. Remove attribute or complete the definition\n" +
                "\twhile processing steps:\n" +
                "\t'git' @ line: 2, col: 5\n" +
                "\t\t'imports' @ line: 1, col: 1";

        assertErrorMessage("errors/imports/005.yml", msg);
    }

    @Test
    public void test006() throws Exception {
        String msg = "(006.yml): Error @ line: 3, col: 13. Invalid value type, expected: STRING, got: INT\n" +
                "\twhile processing steps:\n" +
                "\t'name' @ line: 3, col: 7\n" +
                "\t\t'git' @ line: 2, col: 5\n" +
                "\t\t\t'imports' @ line: 1, col: 1";

        assertErrorMessage("errors/imports/006.yml", msg);
    }

    @Test
    public void test007() throws Exception {
        String msg = "(007.yml): Error @ line: 4, col: 16. Invalid value type, expected: STRING, got: INT\n" +
                "\twhile processing steps:\n" +
                "\t'version' @ line: 4, col: 7\n" +
                "\t\t'git' @ line: 2, col: 5\n" +
                "\t\t\t'imports' @ line: 1, col: 1";

        assertErrorMessage("errors/imports/007.yml", msg);
    }

    @Test
    public void test008() throws Exception {
        String msg = "(008.yml): Error @ line: 5, col: 12. Invalid value type, expected: STRING, got: NULL. Remove attribute or complete the definition\n" +
                "\twhile processing steps:\n" +
                "\t'path' @ line: 5, col: 7\n" +
                "\t\t'git' @ line: 2, col: 5\n" +
                "\t\t\t'imports' @ line: 1, col: 1";

        assertErrorMessage("errors/imports/008.yml", msg);
    }

    @Test
    public void test009() throws Exception {
        String msg = "(009.yml): Error @ line: 8, col: 14. Invalid value type, expected: STRING, got: INT\n" +
                "\twhile processing steps:\n" +
                "\t'org' @ line: 8, col: 9\n" +
                "\t\t'secret' @ line: 7, col: 7\n" +
                "\t\t\t'git' @ line: 2, col: 5\n" +
                "\t\t\t\t'imports' @ line: 1, col: 1";

        assertErrorMessage("errors/imports/009.yml", msg);
    }

    @Test
    public void test010() throws Exception {
        String msg = "(010.yml): Error @ n/a. Mandatory parameter 'name' not found\n" +
                "\twhile processing steps:\n" +
                "\t'secret' @ line: 7, col: 7\n" +
                "\t\t'git' @ line: 2, col: 5\n" +
                "\t\t\t'imports' @ line: 1, col: 1";

        assertErrorMessage("errors/imports/010.yml", msg);
    }

    @Test
    public void test011() throws Exception {
        String msg = "(011.yml): Error @ line: 2, col: 5. Unknown options: ['git-trash' @ line: 2, col: 5], expected: [git, mvn]. Remove invalid options and/or fix indentation\n" +
                "\twhile processing steps:\n" +
                "\t'imports' @ line: 1, col: 1";

        assertErrorMessage("errors/imports/011.yml", msg);
    }

    @Test
    public void test012() throws Exception {
        String msg = "(012.yml): Error @ line: 4, col: 16. Invalid value type, expected: ARRAY_OF_PATTERN, got: STRING\n" +
                "\twhile processing steps:\n" +
                "\t'exclude' @ line: 4, col: 7\n" +
                "\t\t'git' @ line: 2, col: 5\n" +
                "\t\t\t'imports' @ line: 1, col: 1";

        assertErrorMessage("errors/imports/012.yml", msg);
    }

    @Test
    public void test013() throws Exception {
        String msg = "(013.yml): Error @ line: 7, col: 14. Unknown options: ['trash' [STRING] @ line: 7, col: 14], expected: [dest, exclude, name, path, secret, url, version]. Remove invalid options and/or fix indentation\n" +
                "\twhile processing steps:\n" +
                "\t'git' @ line: 2, col: 5\n" +
                "\t\t'imports' @ line: 1, col: 1";

        assertErrorMessage("errors/imports/013.yml", msg);
    }

    @Test
    public void test014() throws Exception {
        String msg = "(014.yml): Error @ line: 2, col: 10. Invalid value type, expected: MVN_IMPORT, got: STRING\n" +
                "\twhile processing steps:\n" +
                "\t'mvn' @ line: 2, col: 5\n" +
                "\t\t'imports' @ line: 1, col: 1";

        assertErrorMessage("errors/imports/014.yml", msg);
    }

    @Test
    public void test015() throws Exception {
        String msg = "(015.yml): Error @ line: 6, col: 11. Invalid value type, expected: PATTERN, got: INT\n" +
                "\twhile processing steps:\n" +
                "\t'exclude' @ line: 4, col: 7\n" +
                "\t\t'git' @ line: 2, col: 5\n" +
                "\t\t\t'imports' @ line: 1, col: 1";

        assertErrorMessage("errors/imports/015.yml", msg);
    }

    @Test
    public void test016() throws Exception {
        String msg = "(016.yml): Error @ line: 6, col: 11. Invalid value type, expected: PATTERN, got: STRING. Error info: Unclosed character class near index 1\n" +
                "[.\n" +
                " ^\n" +
                "\twhile processing steps:\n" +
                "\t'exclude' @ line: 4, col: 7\n" +
                "\t\t'git' @ line: 2, col: 5\n" +
                "\t\t\t'imports' @ line: 1, col: 1";

        assertErrorMessage("errors/imports/016.yml", msg);
    }

    @Test
    public void test101() throws Exception {
        String msg = "(001.yml): Error @ line: 1, col: 11. Invalid value type, expected: TRIGGER, got: STRING\n" +
                "\twhile processing steps:\n" +
                "\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/001.yml", msg);
    }

    @Test
    public void test102() throws Exception {
        String msg = "(002.yml): Error @ line: 2, col: 12. Invalid value type, expected: GITHUB_TRIGGER, got: NULL. Remove attribute or complete the definition\n" +
                "\twhile processing steps:\n" +
                "\t'github' @ line: 2, col: 5\n" +
                "\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/002.yml", msg);
    }

    @Test
    public void test103() throws Exception {
        String msg = "(003.yml): Error @ line: 3, col: 16. Invalid value type, expected: INT, got: STRING\n" +
                "\twhile processing steps:\n" +
                "\t'version' @ line: 3, col: 16\n" +
                "\t\t'github' @ line: 2, col: 5\n" +
                "\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/003.yml", msg);
    }

    @Test
    public void test104() throws Exception {
        String msg = "(004.yml): Error @ n/a. Mandatory parameters 'entryPoint, conditions' not found\n" +
                "\twhile processing steps:\n" +
                "\t'github' @ line: 2, col: 5\n" +
                "\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/004.yml", msg);
    }

    @Test
    public void test105() throws Exception {
        String msg = "(005.yml): Error @ line: 4, col: 19. Invalid value type, expected: STRING, got: INT\n" +
                "\twhile processing steps:\n" +
                "\t'entryPoint' @ line: 4, col: 7\n" +
                "\t\t'github' @ line: 2, col: 5\n" +
                "\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/005.yml", msg);
    }

    @Test
    public void test106() throws Exception {
        String msg = "(006.yml): Error @ n/a. Mandatory parameter 'conditions' not found\n" +
                "\twhile processing steps:\n" +
                "\t'github' @ line: 2, col: 5\n" +
                "\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/006.yml", msg);
    }

    @Test
    public void test107() throws Exception {
        String msg = "(007.yml): Error @ line: 5, col: 19. Invalid value type, expected: GITHUB_TRIGGER_CONDITIONS, got: INT\n" +
                "\twhile processing steps:\n" +
                "\t'conditions' @ line: 5, col: 7\n" +
                "\t\t'github' @ line: 2, col: 5\n" +
                "\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/007.yml", msg);
    }

    @Test
    public void test108() throws Exception {
        String msg = "(008.yml): Error @ n/a. Mandatory parameter 'type' not found\n" +
                "\twhile processing steps:\n" +
                "\t'conditions' @ line: 5, col: 7\n" +
                "\t\t'github' @ line: 2, col: 5\n" +
                "\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/008.yml", msg);
    }

    @Test
    public void test109() throws Exception {
        String msg = "(009.yml): Error @ line: 7, col: 23. Invalid value type, expected: ARRAY_OF_STRING, got: INT\n" +
                "\twhile processing steps:\n" +
                "\t'activeProfiles' @ line: 7, col: 7\n" +
                "\t\t'github' @ line: 2, col: 5\n" +
                "\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/009.yml", msg);
    }

    @Test
    public void test110() throws Exception {
        String msg = "(010.yml): Error @ line: 9, col: 21. Invalid value type, expected: BOOLEAN, got: STRING\n" +
                "\twhile processing steps:\n" +
                "\t'useInitiator' @ line: 9, col: 7\n" +
                "\t\t'github' @ line: 2, col: 5\n" +
                "\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/010.yml", msg);
    }

    @Test
    public void test111() throws Exception {
        String msg = "(011.yml): Error @ line: 10, col: 25. Invalid value type, expected: BOOLEAN, got: STRING\n" +
                "\twhile processing steps:\n" +
                "\t'useEventCommitId' @ line: 10, col: 7\n" +
                "\t\t'github' @ line: 2, col: 5\n" +
                "\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/011.yml", msg);
    }

    @Test
    public void test112() throws Exception {
        String msg = "(012.yml): Error @ line: 11, col: 18. Invalid value type, expected: GITHUB_EXCLUSIVE_MODE, got: INT\n" +
                "\twhile processing steps:\n" +
                "\t'exclusive' @ line: 11, col: 7\n" +
                "\t\t'github' @ line: 2, col: 5\n" +
                "\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/012.yml", msg);
    }

    @Test
    public void test113() throws Exception {
        String msg = "(013.yml): Error @ line: 13, col: 18. Invalid value type, expected: OBJECT, got: INT\n" +
                "\twhile processing steps:\n" +
                "\t'arguments' @ line: 13, col: 7\n" +
                "\t\t'github' @ line: 2, col: 5\n" +
                "\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/013.yml", msg);
    }

    @Test
    public void test114() throws Exception {
        String msg = "(014.yml): Error @ line: 15, col: 20. Invalid value type, expected: PATTERN, got: STRING. Error info: Dangling meta character '*' near index 0\n" +
                "*\n" +
                "^\n" +
                "\twhile processing steps:\n" +
                "\t'githubOrg' @ line: 15, col: 9\n" +
                "\t\t'conditions' @ line: 13, col: 7\n" +
                "\t\t\t'github' @ line: 2, col: 5\n" +
                "\t\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/014.yml", msg);
    }

    @Test
    public void test115() throws Exception {
        String msg = "(015.yml): Error @ n/a. Version 1 of GitHub triggers is not supported\n" +
                "\twhile processing steps:\n" +
                "\t'github' @ line: 2, col: 5\n" +
                "\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/015.yml", msg);
    }

    @Test
    public void test116() throws Exception {
        String msg = "(016.yml): Error @ line: 2, col: 10. Invalid value type, expected: CRON_TRIGGER, got: NULL. Remove attribute or complete the definition\n" +
                "\twhile processing steps:\n" +
                "\t'cron' @ line: 2, col: 5\n" +
                "\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/016.yml", msg);
    }

    @Test
    public void test117() throws Exception {
        String msg = "(017.yml): Error @ n/a. Mandatory parameter 'entryPoint' not found\n" +
                "\twhile processing steps:\n" +
                "\t'cron' @ line: 2, col: 5\n" +
                "\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/017.yml", msg);
    }

    @Test
    public void test118() throws Exception {
        String msg = "(018.yml): Error @ line: 3, col: 13. Invalid value type, expected: STRING, got: INT\n" +
                "\twhile processing steps:\n" +
                "\t'spec' @ line: 3, col: 7\n" +
                "\t\t'cron' @ line: 2, col: 5\n" +
                "\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/018.yml", msg);
    }

    @Test
    public void test119() throws Exception {
        String msg = "(019.yml): Error @ line: 4, col: 19. Invalid value type, expected: STRING, got: INT\n" +
                "\twhile processing steps:\n" +
                "\t'entryPoint' @ line: 4, col: 7\n" +
                "\t\t'cron' @ line: 2, col: 5\n" +
                "\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/019.yml", msg);
    }

    @Test
    public void test120() throws Exception {
        String msg = "(020.yml): Error @ line: 5, col: 23. Invalid value type, expected: ARRAY_OF_STRING, got: INT\n" +
                "\twhile processing steps:\n" +
                "\t'activeProfiles' @ line: 5, col: 7\n" +
                "\t\t'cron' @ line: 2, col: 5\n" +
                "\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/020.yml", msg);
    }

    @Test
    public void test121() throws Exception {
        String msg = "(021.yml): Error @ line: 7, col: 18. Invalid value type, expected: OBJECT, got: INT\n" +
                "\twhile processing steps:\n" +
                "\t'arguments' @ line: 7, col: 7\n" +
                "\t\t'cron' @ line: 2, col: 5\n" +
                "\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/021.yml", msg);
    }

    @Test
    public void test122() throws Exception {
        String msg = "(022.yml): Error @ line: 2, col: 12. Invalid value type, expected: MANUAL_TRIGGER, got: NULL. Remove attribute or complete the definition\n" +
                "\twhile processing steps:\n" +
                "\t'manual' @ line: 2, col: 5\n" +
                "\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/022.yml", msg);
    }

    @Test
    public void test123() throws Exception {
        String msg = "(023.yml): Error @ n/a. Mandatory parameter 'entryPoint' not found\n" +
                "\twhile processing steps:\n" +
                "\t'manual' @ line: 2, col: 5\n" +
                "\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/023.yml", msg);
    }

    @Test
    public void test124() throws Exception {
        String msg = "(024.yml): Error @ line: 5, col: 23. Invalid value type, expected: ARRAY_OF_STRING, got: INT\n" +
                "\twhile processing steps:\n" +
                "\t'activeProfiles' @ line: 5, col: 7\n" +
                "\t\t'manual' @ line: 2, col: 5\n" +
                "\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/024.yml", msg);
    }

    @Test
    public void test125() throws Exception {
        String msg = "(025.yml): Error @ n/a. Version 1 of oneops trigger not supported\n" +
                "\twhile processing steps:\n" +
                "\t'oneops' @ line: 2, col: 5\n" +
                "\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/025.yml", msg);
    }

    @Test
    public void test126() throws Exception {
        String msg = "(026.yml): Error @ n/a. Mandatory parameter 'conditions' not found\n" +
                "\twhile processing steps:\n" +
                "\t'oneops' @ line: 2, col: 5\n" +
                "\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/026.yml", msg);
    }

    @Test
    public void test127() throws Exception {
        String msg = "(027.yml): Error @ line: 7, col: 14. Unknown options: ['trash' [STRING] @ line: 7, col: 14], expected: [activeProfiles, arguments, conditions, entryPoint, exclusive, useInitiator, version]. Remove invalid options and/or fix indentation\n" +
                "\twhile processing steps:\n" +
                "\t'oneops' @ line: 2, col: 5\n" +
                "\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/027.yml", msg);
    }

    @Test
    public void test128() throws Exception {
        String msg = "(028.yml): Error @ line: 5, col: 19. Invalid value type, expected: OBJECT, got: INT\n" +
                "\twhile processing steps:\n" +
                "\t'conditions' @ line: 5, col: 7\n" +
                "\t\t'example' @ line: 2, col: 5\n" +
                "\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/028.yml", msg);
    }

    @Test
    public void test129() throws Exception {
        String msg = "(029.yml): Error @ line: 8, col: 13. Invalid value type, expected: PATTERN, got: INT\n" +
                "\twhile processing steps:\n" +
                "\t'githubOrg' @ line: 7, col: 9\n" +
                "\t\t'conditions' @ line: 5, col: 7\n" +
                "\t\t\t'github' @ line: 2, col: 5\n" +
                "\t\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/029.yml", msg);
    }

    @Test
    public void test130() throws Exception {
        String msg = "(030.yml): Error @ line: 5, col: 17. Invalid value type, expected: TIMEZONE, got: STRING. Error info: Unknown timezone: 'test'\n" +
                "\twhile processing steps:\n" +
                "\t'timezone' @ line: 5, col: 7\n" +
                "\t\t'cron' @ line: 2, col: 5\n" +
                "\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/030.yml", msg);
    }

    @Test
    public void test131() throws Exception {
        String msg = "(031.yml): Error @ line: 6, col: 14. Unknown options: ['trash' [STRING] @ line: 6, col: 14], expected: [activeProfiles, arguments, entryPoint, exclusive, runAs, spec, timezone]. Remove invalid options and/or fix indentation\n" +
                "\twhile processing steps:\n" +
                "\t'cron' @ line: 2, col: 5\n" +
                "\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/031.yml", msg);
    }

    @Test
    public void test131_1() throws Exception {
        String msg = "(031_1.yml): Error @ n/a. Mandatory parameter 'withSecret' not found\n" +
                "\twhile processing steps:\n" +
                "\t'runAs' @ line: 5, col: 7\n" +
                "\t\t'cron' @ line: 2, col: 5\n" +
                "\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/031_1.yml", msg);
    }

    @Test
    public void test132() throws Exception {
        String msg = "(032.yml): Error @ line: 8, col: 13. Invalid value type, expected: GITHUB_REPOSITORY_INFO, got: INT\n" +
                "\twhile processing steps:\n" +
                "\t'repositoryInfo' @ line: 7, col: 9\n" +
                "\t\t'conditions' @ line: 5, col: 7\n" +
                "\t\t\t'github' @ line: 2, col: 5\n" +
                "\t\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/032.yml", msg);
    }

    @Test
    public void test133() throws Exception {
        String msg = "(033.yml): Error @ line: 7, col: 24. Invalid value type, expected: REPOSITORY_INFO, got: NULL. Remove attribute or complete the definition\n" +
                "\twhile processing steps:\n" +
                "\t'repositoryInfo' @ line: 7, col: 9\n" +
                "\t\t'conditions' @ line: 5, col: 7\n" +
                "\t\t\t'github' @ line: 2, col: 5\n" +
                "\t\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/033.yml", msg);
    }

    @Test
    public void test134() throws Exception {
        String msg = "(034.yml): Error @ line: 8, col: 20. Unknown options: ['trash' [STRING] @ line: 8, col: 20], expected: [branch, enabled, projectId, repository, repositoryId]. Remove invalid options and/or fix indentation\n" +
                "\twhile processing steps:\n" +
                "\t'repositoryInfo' @ line: 7, col: 9\n" +
                "\t\t'conditions' @ line: 5, col: 7\n" +
                "\t\t\t'github' @ line: 2, col: 5\n" +
                "\t\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/034.yml", msg);
    }

    @Test
    public void test135() throws Exception {
        String msg = "(035.yml): Error @ line: 8, col: 22. Invalid value type, expected: BOOLEAN, got: STRING\n" +
                "\twhile processing steps:\n" +
                "\t'enabled' @ line: 8, col: 13\n" +
                "\t\t'repositoryInfo' @ line: 7, col: 9\n" +
                "\t\t\t'conditions' @ line: 5, col: 7\n" +
                "\t\t\t\t'github' @ line: 2, col: 5\n" +
                "\t\t\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/035.yml", msg);
    }

    @Test
    public void test137() throws Exception {
        String msg = "(037.yml): Error @ n/a. One of mandatory parameters 'group, groupBy' not found\n" +
                "\twhile processing steps:\n" +
                "\t'exclusive' @ line: 5, col: 7\n" +
                "\t\t'github' @ line: 2, col: 5\n" +
                "\t\t\t'triggers' @ line: 1, col: 1";

        assertErrorMessage("errors/triggers/037.yml", msg);
    }

    @Test
    public void test200() throws Exception {
        String msg =
                "(000.yml): Error @ line: 3, col: 12. Invalid value type, expected: STRING, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'task' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/tasks/000.yml", msg);
    }

    @Test
    public void test201() throws Exception {
        String msg =
                "(001.yml): Error @ line: 3, col: 13. Invalid value type, expected: STRING, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'task' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/tasks/001.yml", msg);
    }

    @Test
    public void test202() throws Exception {
        String msg =
                "(002.yml): Error @ line: 4, col: 11. Invalid value type, expected: STRING or OBJECT, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'out' @ line: 4, col: 7\n" +
                        "\t\t'task' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/tasks/002.yml", msg);
    }

    @Test
    public void test203() throws Exception {
        String msg =
                "(003.yml): Error @ line: 4, col: 12. Invalid value type, expected: STRING or OBJECT, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'out' @ line: 4, col: 7\n" +
                        "\t\t'task' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/tasks/003.yml", msg);
    }

    @Test
    public void test205() throws Exception {
        String msg =
                "(005.yml): Error @ line: 5, col: 10. Invalid value type, expected: OBJECT or EXPRESSION, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'in' @ line: 5, col: 7\n" +
                        "\t\t'task' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/tasks/005.yml", msg);
    }

    @Test
    public void test206() throws Exception {
        String msg =
                "(006.yml): Error @ line: 5, col: 11. Invalid value type, expected: OBJECT or EXPRESSION, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'in' @ line: 5, col: 7\n" +
                        "\t\t'task' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/tasks/006.yml", msg);
    }

    @Test
    @Disabled("we allow nulls in kv now")
    public void test207() throws Exception {
        String msg =
                "(007.yml): Error @ line: 8, col: 12. Invalid value type of 'k3' parameter, expected: NON_NULL, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'task' @ line: 3, col: 7";

        assertErrorMessage("errors/tasks/007.yml", msg);
    }

    @Test
    public void test208() throws Exception {
        String msg =
                "(008.yml): Error @ line: 9, col: 17. Invalid value type, expected: NON_NULL, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'withItems' @ line: 9, col: 7\n" +
                        "\t\t'task' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/tasks/008.yml", msg);
    }

    @Test
    public void test209() throws Exception {
        String msg =
                "(009.yml): Error @ line: 10, col: 13. Invalid value type, expected: RETRY, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'retry' @ line: 10, col: 7\n" +
                        "\t\t'task' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/tasks/009.yml", msg);
    }

    @Test
    public void test210() throws Exception {
        String msg =
                "(010.yml): Error @ line: 10, col: 14. Invalid value type, expected: RETRY, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'retry' @ line: 10, col: 7\n" +
                        "\t\t'task' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/tasks/010.yml", msg);
    }

    @Test
    public void test211() throws Exception {
        String msg =
                "(011.yml): Error @ line: 11, col: 15. Invalid value type, expected: INT or EXPRESSION, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'times' @ line: 11, col: 9\n" +
                        "\t\t'retry' @ line: 10, col: 7\n" +
                        "\t\t\t'task' @ line: 3, col: 7\n" +
                        "\t\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/tasks/011.yml", msg);
    }

    @Test
    public void test212() throws Exception {
        String msg =
                "(012.yml): Error @ line: 12, col: 15. Invalid value type, expected: INT or EXPRESSION, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'delay' @ line: 12, col: 9\n" +
                        "\t\t'retry' @ line: 10, col: 7\n" +
                        "\t\t\t'task' @ line: 3, col: 7\n" +
                        "\t\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/tasks/012.yml", msg);
    }

    @Test
    public void test213() throws Exception {
        String msg =
                "(013.yml): Error @ line: 13, col: 12. Invalid value type, expected: OBJECT, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'in' @ line: 13, col: 9\n" +
                        "\t\t'retry' @ line: 10, col: 7\n" +
                        "\t\t\t'task' @ line: 3, col: 7\n" +
                        "\t\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/tasks/013.yml", msg);
    }

    @Test
    public void test214() throws Exception {
        String msg =
                "(014.yml): Error @ line: 13, col: 13. Invalid value type, expected: OBJECT, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'in' @ line: 13, col: 9\n" +
                        "\t\t'retry' @ line: 10, col: 7\n" +
                        "\t\t\t'task' @ line: 3, col: 7\n" +
                        "\t\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/tasks/014.yml", msg);
    }

    @Test
    public void test215() throws Exception {
        String msg =
                "(015.yml): Error @ line: 15, col: 14. Unknown options: ['trash' [STRING] @ line: 15, col: 14], expected: [error, ignoreErrors, in, loop, meta, name, out, parallelWithItems, retry, withItems]. Remove invalid options and/or fix indentation\n" +
                        "\twhile processing steps:\n" +
                        "\t'task' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/tasks/015.yml", msg);
    }

    @Test
    public void test216() throws Exception {
        String msg =
                "(016.yml): Error @ line: 15, col: 12. Invalid value type, expected: OBJECT, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'meta' @ line: 15, col: 7\n" +
                        "\t\t'task' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/tasks/016.yml", msg);
    }

    @Test
    public void test217() throws Exception {
        String msg =
                "(017.yml): Error @ line: 15, col: 13. Invalid value type, expected: OBJECT, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'meta' @ line: 15, col: 7\n" +
                        "\t\t'task' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/tasks/017.yml", msg);
    }

    @Test
    public void test218() throws Exception {
        String msg =
                "(018.yml): Error @ line: 11, col: 16. Invalid value type, expected: INT or EXPRESSION, got: STRING\n" +
                        "\twhile processing steps:\n" +
                        "\t'times' @ line: 11, col: 9\n" +
                        "\t\t'retry' @ line: 10, col: 7\n" +
                        "\t\t\t'task' @ line: 3, col: 7\n" +
                        "\t\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/tasks/018.yml", msg);
    }

    @Test
    public void test219() throws Exception {
        String msg =
                "(019.yml): Error @ line: 3, col: 13. Invalid value type, expected: STRING, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'name' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/tasks/019.yml", msg);
    }

    @Test
    public void test220() throws Exception {
        String msg =
                "(020.yml): Error @ line: 7, col: 15. Invalid value: trash, expected: [SERIAL, PARALLEL]\n" +
                        "\twhile processing steps:\n" +
                        "\t'mode' @ line: 7, col: 9\n" +
                        "\t\t'loop' @ line: 5, col: 7\n" +
                        "\t\t\t'task' @ line: 4, col: 7\n" +
                        "\t\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/tasks/020.yml", msg);
    }

    @Test
    public void test221() throws Exception {
        String msg =
                "(021.yml): Error @ line: 7, col: 22. Invalid value type, expected: int or expression, got: STRING\n" +
                        "\twhile processing steps:\n" +
                        "\t'parallelism' @ line: 7, col: 9\n" +
                        "\t\t'loop' @ line: 5, col: 7\n" +
                        "\t\t\t'task' @ line: 4, col: 7\n" +
                        "\t\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/tasks/021.yml", msg);
    }

    @Test
    public void test300() throws Exception {
        String msg =
                "(000.yml): Error @ line: 3, col: 12. Invalid value type, expected: STRING, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'call' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/flowCall/000.yml", msg);
    }

    @Test
    public void test301() throws Exception {
        String msg =
                "(001.yml): Error @ line: 3, col: 13. Invalid value type, expected: STRING, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'call' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/flowCall/001.yml", msg);
    }

    @Test
    public void test302() throws Exception {
        String msg =
                "(002.yml): Error @ line: 4, col: 11. Invalid value type, expected: STRING or ARRAY_OF_STRING or OBJECT, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'out' @ line: 4, col: 7\n" +
                        "\t\t'call' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/flowCall/002.yml", msg);
    }

    @Test
    public void test303() throws Exception {
        String msg =
                "(003.yml): Error @ line: 4, col: 12. Invalid value type, expected: STRING or ARRAY_OF_STRING or OBJECT, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'out' @ line: 4, col: 7\n" +
                        "\t\t'call' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/flowCall/003.yml", msg);
    }

    @Test
    public void test305() throws Exception {
        String msg =
                "(005.yml): Error @ line: 5, col: 10. Invalid value type, expected: OBJECT or EXPRESSION, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'in' @ line: 5, col: 7\n" +
                        "\t\t'call' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/flowCall/005.yml", msg);
    }

    @Test
    public void test306() throws Exception {
        String msg =
                "(006.yml): Error @ line: 5, col: 11. Invalid value type, expected: OBJECT or EXPRESSION, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'in' @ line: 5, col: 7\n" +
                        "\t\t'call' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/flowCall/006.yml", msg);
    }

    @Test
    @Disabled("we allow nulls in kv now")
    public void test307() throws Exception {
        String msg =
                "(003.yml): Error @ line: 4, col: 12. Invalid value type, expected: STRING, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'out' @ line: 4, col: 7\n" +
                        "\t\t'call' @ line: 3, col: 7";

        assertErrorMessage("errors/flowCall/007.yml", msg);
    }

    @Test
    public void test308() throws Exception {
        String msg =
                "(008.yml): Error @ line: 9, col: 17. Invalid value type, expected: NON_NULL, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'withItems' @ line: 9, col: 7\n" +
                        "\t\t'call' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/flowCall/008.yml", msg);
    }

    @Test
    public void test309() throws Exception {
        String msg =
                "(009.yml): Error @ line: 10, col: 13. Invalid value type, expected: RETRY, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'retry' @ line: 10, col: 7\n" +
                        "\t\t'call' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/flowCall/009.yml", msg);
    }

    @Test
    public void test310() throws Exception {
        String msg =
                "(010.yml): Error @ line: 10, col: 14. Invalid value type, expected: RETRY, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'retry' @ line: 10, col: 7\n" +
                        "\t\t'call' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/flowCall/010.yml", msg);
    }

    @Test
    public void test311() throws Exception {
        String msg =
                "(011.yml): Error @ line: 11, col: 15. Invalid value type, expected: INT or EXPRESSION, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'times' @ line: 11, col: 9\n" +
                        "\t\t'retry' @ line: 10, col: 7\n" +
                        "\t\t\t'call' @ line: 3, col: 7\n" +
                        "\t\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/flowCall/011.yml", msg);
    }

    @Test
    public void test312() throws Exception {
        String msg =
                "(012.yml): Error @ line: 12, col: 15. Invalid value type, expected: INT or EXPRESSION, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'delay' @ line: 12, col: 9\n" +
                        "\t\t'retry' @ line: 10, col: 7\n" +
                        "\t\t\t'call' @ line: 3, col: 7\n" +
                        "\t\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/flowCall/012.yml", msg);
    }

    @Test
    public void test313() throws Exception {
        String msg =
                "(013.yml): Error @ line: 13, col: 12. Invalid value type, expected: OBJECT, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'in' @ line: 13, col: 9\n" +
                        "\t\t'retry' @ line: 10, col: 7\n" +
                        "\t\t\t'call' @ line: 3, col: 7\n" +
                        "\t\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/flowCall/013.yml", msg);
    }

    @Test
    public void test314() throws Exception {
        String msg =
                "(014.yml): Error @ line: 13, col: 13. Invalid value type, expected: OBJECT, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'in' @ line: 13, col: 9\n" +
                        "\t\t'retry' @ line: 10, col: 7\n" +
                        "\t\t\t'call' @ line: 3, col: 7\n" +
                        "\t\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/flowCall/014.yml", msg);
    }

    @Test
    public void test315() throws Exception {
        String msg =
                "(015.yml): Error @ line: 15, col: 14. Unknown options: ['trash' [STRING] @ line: 15, col: 14], expected: [error, in, loop, meta, name, out, parallelWithItems, retry, withItems]. Remove invalid options and/or fix indentation\n" +
                        "\twhile processing steps:\n" +
                        "\t'call' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/flowCall/015.yml", msg);
    }

    @Test
    public void test316() throws Exception {
        String msg =
                "(016.yml): Error @ line: 15, col: 12. Invalid value type, expected: OBJECT, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'meta' @ line: 15, col: 7\n" +
                        "\t\t'call' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/flowCall/016.yml", msg);
    }

    @Test
    public void test317() throws Exception {
        String msg =
                "(017.yml): Error @ line: 15, col: 13. Invalid value type, expected: OBJECT, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'meta' @ line: 15, col: 7\n" +
                        "\t\t'call' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/flowCall/017.yml", msg);
    }

    @Test
    public void test318() throws Exception {
        String msg =
                "(018.yml): Error @ line: 5, col: 11. Invalid value type, expected: STRING, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'out' @ line: 4, col: 7\n" +
                        "\t\t'call' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/flowCall/018.yml", msg);
    }

    @Test
    public void test400() throws Exception {
        String msg =
                "(000.yml): Error @ line: 3, col: 18. Invalid value type, expected: STRING, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'checkpoint' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/checkpoint/000.yml", msg);
    }

    @Test
    public void test401() throws Exception {
        String msg =
                "(001.yml): Error @ line: 3, col: 19. Invalid value type, expected: STRING, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'checkpoint' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/checkpoint/001.yml", msg);
    }

    @Test
    public void test402() throws Exception {
        String msg =
                "(002.yml): Error @ line: 4, col: 14. Unknown options: ['trash' [STRING] @ line: 4, col: 14], expected: [meta]. Remove invalid options and/or fix indentation\n" +
                        "\twhile processing steps:\n" +
                        "\t'checkpoint' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/checkpoint/002.yml", msg);
    }

    @Test
    public void test403() throws Exception {
        String msg =
                "(003.yml): Error @ line: 4, col: 13. Invalid value type, expected: OBJECT, got: STRING\n" +
                        "\twhile processing steps:\n" +
                        "\t'meta' @ line: 4, col: 7\n" +
                        "\t\t'checkpoint' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/checkpoint/003.yml", msg);
    }

    @Test
    public void test404() throws Exception {
        String msg =
                "(004.yml): Error @ line: 4, col: 12. Invalid value type, expected: OBJECT, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'meta' @ line: 4, col: 7\n" +
                        "\t\t'checkpoint' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/checkpoint/004.yml", msg);
    }

    @Test
    public void test405() throws Exception {
        String msg =
                "(005.yml): Error @ line: 6, col: 14. Unknown options: ['trash' [INT] @ line: 6, col: 14], expected: [meta]. Remove invalid options and/or fix indentation\n" +
                        "\twhile processing steps:\n" +
                        "\t'checkpoint' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/checkpoint/005.yml", msg);
    }

    @Test
    public void test600() throws Exception {
        String msg =
                "(000.yml): Error @ line: 3, col: 12. Invalid value type, expected: EXPRESSION, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'expr' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/expression/000.yml", msg);
    }

    @Test
    public void test601() throws Exception {
        String msg =
                "(001.yml): Error @ line: 3, col: 13. Invalid value type, expected: EXPRESSION, got: STRING\n" +
                        "\twhile processing steps:\n" +
                        "\t'expr' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/expression/001.yml", msg);
    }

    @Test
    public void test602() throws Exception {
        String msg =
                "(002.yml): Error @ line: 4, col: 11. Invalid value type, expected: STRING or OBJECT, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'out' @ line: 4, col: 7\n" +
                        "\t\t'expr' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/expression/002.yml", msg);
    }

    @Test
    public void test603() throws Exception {
        String msg =
                "(003.yml): Error @ line: 4, col: 12. Invalid value type, expected: STRING or OBJECT, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'out' @ line: 4, col: 7\n" +
                        "\t\t'expr' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/expression/003.yml", msg);
    }

    @Test
    public void test605() throws Exception {
        String msg =
                "(005.yml): Error @ line: 5, col: 12. Invalid value type, expected: OBJECT, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'meta' @ line: 5, col: 7\n" +
                        "\t\t'expr' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/expression/005.yml", msg);
    }

    @Test
    public void test606() throws Exception {
        String msg =
                "(006.yml): Error @ line: 5, col: 13. Invalid value type, expected: OBJECT, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'meta' @ line: 5, col: 7\n" +
                        "\t\t'expr' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/expression/006.yml", msg);
    }

    @Test
    public void test607() throws Exception {
        String msg =
                "(007.yml): Error @ line: 7, col: 13. Invalid value type, expected: ARRAY_OF_STEP, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'error' @ line: 7, col: 7\n" +
                        "\t\t'expr' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/expression/007.yml", msg);
    }

    @Test
    public void test608() throws Exception {
        String msg =
                "(008.yml): Error @ line: 7, col: 14. Invalid value type, expected: ARRAY_OF_STEP, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'error' @ line: 7, col: 7\n" +
                        "\t\t'expr' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/expression/008.yml", msg);
    }

    @Test
    public void test609() throws Exception {
        String msg =
                "(009.yml): Error @ line: 8, col: 10. Invalid value type, expected: STEP, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'error' @ line: 7, col: 7\n" +
                        "\t\t'expr' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/expression/009.yml", msg);
    }

    @Test
    public void test610() throws Exception {
        String msg =
                "(010.yml): Error @ line: 8, col: 11. Invalid value type, expected: STEP, got: STRING\n" +
                        "\twhile processing steps:\n" +
                        "\t'error' @ line: 7, col: 7\n" +
                        "\t\t'expr' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/expression/010.yml", msg);
    }

    @Test
    public void test700() throws Exception {
        String msg =
                "(000.yml): Error @ line: 3, col: 11. Invalid value type, expected: ARRAY_OF_STEP, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'try' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/group/000.yml", msg);
    }

    @Test
    public void test701() throws Exception {
        String msg =
                "(001.yml): Error @ line: 3, col: 12. Invalid value type, expected: ARRAY_OF_STEP, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'try' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/group/001.yml", msg);
    }

    @Test
    public void test702() throws Exception {
        String msg =
                "(002.yml): Error @ line: 4, col: 10. Invalid value type, expected: STEP, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'try' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/group/002.yml", msg);
    }

    @Test
    public void test703() throws Exception {
        String msg =
                "(003.yml): Error @ line: 5, col: 13. Unknown options: ['trash' [NULL] @ line: 5, col: 13], expected: [error, loop, meta, name, out, parallelWithItems, withItems]. Remove invalid options and/or fix indentation\n" +
                        "\twhile processing steps:\n" +
                        "\t'try' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/group/003.yml", msg);
    }

    @Test
    public void test704() throws Exception {
        String msg =
                "(004.yml): Error @ line: 5, col: 13. Invalid value type, expected: ARRAY_OF_STEP, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'error' @ line: 5, col: 7\n" +
                        "\t\t'try' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/group/004.yml", msg);
    }

    @Test
    public void test705() throws Exception {
        String msg =
                "(005.yml): Error @ line: 7, col: 12. Invalid value type, expected: OBJECT, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'meta' @ line: 7, col: 7\n" +
                        "\t\t'try' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/group/005.yml", msg);
    }

    @Test
    public void test706() throws Exception {
        String msg =
                "(006.yml): Error @ line: 9, col: 17. Invalid value type, expected: NON_NULL, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'withItems' @ line: 9, col: 7\n" +
                        "\t\t'try' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/group/006.yml", msg);
    }

    @Test
    public void test707() throws Exception {
        String msg =
                "(007.yml): Error @ line: 11, col: 13. Unknown options: ['trash' [NULL] @ line: 11, col: 13], expected: [error, loop, meta, name, out, parallelWithItems, withItems]. Remove invalid options and/or fix indentation\n" +
                        "\twhile processing steps:\n" +
                        "\t'try' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/group/007.yml", msg);
    }

    @Test
    public void test708() throws Exception {
        String msg =
                "(008.yml): Error @ line: 3, col: 13. Invalid value type, expected: ARRAY_OF_STEP, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'block' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/group/008.yml", msg);
    }

    @Test
    public void test800() throws Exception {
        String msg =
                "(000.yml): Error @ line: 3, col: 16. Invalid value type, expected: ARRAY_OF_STEP, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'parallel' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/parallel/000.yml", msg);
    }

    @Test
    public void test801() throws Exception {
        String msg =
                "(001.yml): Error @ line: 3, col: 17. Invalid value type, expected: ARRAY_OF_STEP, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'parallel' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/parallel/001.yml", msg);
    }

    @Test
    public void test802() throws Exception {
        String msg =
                "(002.yml): Error @ line: 4, col: 11. Invalid value type, expected: STEP, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'parallel' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/parallel/002.yml", msg);
    }

    @Test
    public void test803() throws Exception {
        String msg =
                "(003.yml): Error @ line: 5, col: 13. Unknown options: ['trash' [NULL] @ line: 5, col: 13], expected: [meta, out]. Remove invalid options and/or fix indentation\n" +
                        "\twhile processing steps:\n" +
                        "\t'parallel' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/parallel/003.yml", msg);
    }

    @Test
    public void test804() throws Exception {
        String msg =
                "(004.yml): Error @ line: 5, col: 12. Invalid value type, expected: OBJECT, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'meta' @ line: 5, col: 7\n" +
                        "\t\t'parallel' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/parallel/004.yml", msg);
    }

    @Test
    public void test805() throws Exception {
        String msg =
                "(005.yml): Error @ line: 7, col: 13. Unknown options: ['trash' [NULL] @ line: 7, col: 13], expected: [meta, out]. Remove invalid options and/or fix indentation\n" +
                        "\twhile processing steps:\n" +
                        "\t'parallel' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/parallel/005.yml", msg);
    }

    @Test
    public void test900() throws Exception {
        String msg =
                "(000.yml): Error @ line: 1, col: 7. Invalid value type, expected: FORMS, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'forms' @ line: 1, col: 1";

        assertErrorMessage("errors/forms/000.yml", msg);
    }

    @Test
    public void test901() throws Exception {
        String msg =
                "(001.yml): Error @ line: 2, col: 6. Invalid value type, expected: ARRAY_OF_FORM_FIELD, got: STRING\n" +
                        "\twhile processing steps:\n" +
                        "\t'k' @ line: 2, col: 3\n" +
                        "\t\t'forms' @ line: 1, col: 1";

        assertErrorMessage("errors/forms/001.yml", msg);
    }

    @Test
    public void test902() throws Exception {
        String msg =
                "(002.yml): Error @ line: 2, col: 10. Invalid value type, expected: ARRAY_OF_FORM_FIELD, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'myForm' @ line: 2, col: 3\n" +
                        "\t\t'forms' @ line: 1, col: 1";

        assertErrorMessage("errors/forms/002.yml", msg);
    }

    @Test
    public void test903() throws Exception {
        String msg =
                "(003.yml): Error @ line: 3, col: 7. Invalid value type, expected: FORM_FIELD, got: STRING\n" +
                        "\twhile processing steps:\n" +
                        "\t'myForm' @ line: 2, col: 3\n" +
                        "\t\t'forms' @ line: 1, col: 1";

        assertErrorMessage("errors/forms/003.yml", msg);
    }

    @Test
    public void test904() throws Exception {
        String msg =
                "(004.yml): Error @ line: 3, col: 16. Invalid value type, expected: FORM_FIELD, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'myForm' @ line: 2, col: 3\n" +
                        "\t\t'forms' @ line: 1, col: 1";

        assertErrorMessage("errors/forms/004.yml", msg);
    }

    @Test
    public void test905() throws Exception {
        String msg =
                "(005.yml): Error @ line: 3, col: 17. Unknown options: ['error' [INT] @ line: 3, col: 123], expected: [label, value, allow, type, pattern, inputType, readOnly, placeholder, search]. Remove invalid options and/or fix indentation\n" +
                        "\twhile processing steps:\n" +
                        "\t'fullName' @ line: 3, col: 7\n" +
                        "\t\t'myForm' @ line: 2, col: 3\n" +
                        "\t\t\t'forms' @ line: 1, col: 1";

        assertErrorMessage("errors/forms/005.yml", msg);
    }

    @Test
    public void test906() throws Exception {
        String msg =
                "(006.yml): Error @ line: 3, col: 26. Invalid value type, expected: STRING, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'fullName' @ line: 3, col: 7\n" +
                        "\t\t'myForm' @ line: 2, col: 3\n" +
                        "\t\t\t'forms' @ line: 1, col: 1";

        assertErrorMessage("errors/forms/006.yml", msg);
    }

    @Test
    public void test907() throws Exception {
        String msg =
                "(007.yml): Error @ line: 3, col: 25. Invalid value type, expected: STRING, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'fullName' @ line: 3, col: 7\n" +
                        "\t\t'myForm' @ line: 2, col: 3\n" +
                        "\t\t\t'forms' @ line: 1, col: 1";

        assertErrorMessage("errors/forms/007.yml", msg);
    }

    @Test
    public void test908() throws Exception {
        String msg =
                "(008.yml): Error @ n/a. Mandatory parameter 'type' not found\n" +
                        "\twhile processing steps:\n" +
                        "\t'fullName' @ line: 3, col: 7\n" +
                        "\t\t'myForm' @ line: 2, col: 3\n" +
                        "\t\t\t'forms' @ line: 1, col: 1";

        assertErrorMessage("errors/forms/008.yml", msg);
    }

    @Test
    public void test909() throws Exception {
        String msg =
                "(009.yml): Error @ line: 3, col: 25. Invalid value: 123, expected: [string, int, decimal, boolean, file, date, dateTime]\n" +
                        "\twhile processing steps:\n" +
                        "\t'fullName' @ line: 3, col: 7\n" +
                        "\t\t'myForm' @ line: 2, col: 3\n" +
                        "\t\t\t'forms' @ line: 1, col: 1";

        assertErrorMessage("errors/forms/009.yml", msg);
    }

    @Test
    public void test910() throws Exception {
        String msg =
                "(010.yml): Error @ line: 3, col: 64. Invalid value type, expected: BOOLEAN, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'fullName' @ line: 3, col: 7\n" +
                        "\t\t'myForm' @ line: 2, col: 3\n" +
                        "\t\t\t'forms' @ line: 1, col: 1";

        assertErrorMessage("errors/forms/010.yml", msg);
    }

    @Test
    public void test911() throws Exception {
        String msg =
                "(011.yml): Error @ line: 1, col: 8. Invalid value type, expected: FORMS, got: STRING\n" +
                        "\twhile processing steps:\n" +
                        "\t'forms' @ line: 1, col: 1";

        assertErrorMessage("errors/forms/011.yml", msg);
    }

    @Test
    public void test1000() throws Exception {
        String msg =
                "(000.yml): Error @ line: 3, col: 12. Invalid value type, expected: STRING, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'form' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/formCall/000.yml", msg);
    }

    @Test
    public void test1001() throws Exception {
        String msg =
                "(001.yml): Error @ line: 3, col: 13. Invalid value type, expected: STRING, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'form' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/formCall/001.yml", msg);
    }

    @Test
    public void test1002() throws Exception {
        String msg =
                "(002.yml): Error @ line: 4, col: 9. Unknown options: ['a' [NULL] @ line: 4, col: 9, 'b' [NULL] @ line: 6, col: 9], expected: [fields, runAs, saveSubmittedBy, values, yield]. Remove invalid options and/or fix indentation\n" +
                        "\twhile processing steps:\n" +
                        "\t'form' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/formCall/002.yml", msg);
    }

    @Test
    public void test1003() throws Exception {
        String msg =
                "(003.yml): Error @ line: 4, col: 14. Invalid value type, expected: BOOLEAN, got: STRING\n" +
                        "\twhile processing steps:\n" +
                        "\t'yield' @ line: 4, col: 7\n" +
                        "\t\t'form' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/formCall/003.yml", msg);
    }

    @Test
    public void test1004() throws Exception {
        String msg =
                "(004.yml): Error @ line: 5, col: 23. Invalid value type, expected: BOOLEAN, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'saveSubmittedBy' @ line: 5, col: 7\n" +
                        "\t\t'form' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/formCall/004.yml", msg);
    }

    @Test
    public void test1005() throws Exception {
        String msg =
                "(005.yml): Error @ line: 6, col: 14. Invalid value type, expected: OBJECT or EXPRESSION, got: ARRAY\n" +
                        "\twhile processing steps:\n" +
                        "\t'runAs' @ line: 6, col: 7\n" +
                        "\t\t'form' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/formCall/005.yml", msg);
    }

    @Test
    public void test1006() throws Exception {
        String msg =
                "(006.yml): Error @ line: 10, col: 15. Invalid value type, expected: OBJECT or EXPRESSION, got: ARRAY\n" +
                        "\twhile processing steps:\n" +
                        "\t'values' @ line: 10, col: 7\n" +
                        "\t\t'form' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/formCall/006.yml", msg);
    }

    @Test
    public void test1007() throws Exception {
        String msg =
                "(007.yml): Error @ line: 12, col: 15. Invalid value type, expected: ARRAY_OF_FORM_FIELD or EXPRESSION, got: STRING\n" +
                        "\twhile processing steps:\n" +
                        "\t'fields' @ line: 12, col: 7\n" +
                        "\t\t'form' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/formCall/007.yml", msg);
    }

    @Test
    public void test1008() throws Exception {
        String msg =
                "(008.yml): Error @ line: 4, col: 15. Invalid value type, expected: ARRAY_OF_FORM_FIELD or EXPRESSION, got: STRING\n" +
                        "\twhile processing steps:\n" +
                        "\t'fields' @ line: 4, col: 7\n" +
                        "\t\t'form' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/formCall/008.yml", msg);
    }

    @Test
    public void test1100() throws Exception {
        String msg =
                "(000.yml): Error @ line: 1, col: 7. Invalid value type, expected: FLOWS, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/flows/000.yml", msg);
    }

    @Test
    public void test1101() throws Exception {
        String msg =
                "(001.yml): Error @ line: 1, col: 8. Invalid value type, expected: FLOWS, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/flows/001.yml", msg);
    }

    @Test
    public void test1102() throws Exception {
        String msg =
                "(002.yml): Error @ line: 2, col: 8. Invalid value type, expected: FLOW, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'main' @ line: 2, col: 3\n" +
                        "\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/flows/002.yml", msg);
    }

    @Test
    public void test1200() throws Exception {
        String msg =
                "(000.yml): Error @ line: 1, col: 10. Invalid value type, expected: PROFILES, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'profiles' @ line: 1, col: 1";

        assertErrorMessage("errors/profiles/000.yml", msg);
    }

    @Test
    public void test1201() throws Exception {
        String msg =
                "(001.yml): Error @ line: 2, col: 14. Invalid value type, expected: PROFILE, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'myProfile' @ line: 2, col: 3\n" +
                        "\t\t'profiles' @ line: 1, col: 1";

        assertErrorMessage("errors/profiles/001.yml", msg);
    }

    @Test
    public void test1202() throws Exception {
        String msg =
                "(002.yml): Error @ line: 3, col: 12. Invalid value type, expected: FLOWS, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'flows' @ line: 3, col: 5\n" +
                        "\t\t'myProfile' @ line: 2, col: 3\n" +
                        "\t\t\t'profiles' @ line: 1, col: 1";

        assertErrorMessage("errors/profiles/002.yml", msg);
    }

    @Test
    public void test1203() throws Exception {
        String msg =
                "(003.yml): Error @ line: 3, col: 12. Unknown options: ['trash' [INT] @ line: 3, col: 12], expected: [configuration, flows, forms]. Remove invalid options and/or fix indentation\n" +
                        "\twhile processing steps:\n" +
                        "\t'myProfile' @ line: 2, col: 3\n" +
                        "\t\t'profiles' @ line: 1, col: 1";

        assertErrorMessage("errors/profiles/003.yml", msg);
    }

    @Test
    public void test1300() throws Exception {
        String msg =
                "(000.yml): Error @ line: 1, col: 15. Invalid value type, expected: CONFIGURATION, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/000.yml", msg);
    }

    @Test
    public void test1301() throws Exception {
        String msg =
                "(001.yml): Error @ line: 2, col: 14. Invalid value type, expected: STRING, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'entryPoint' @ line: 2, col: 3\n" +
                        "\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/001.yml", msg);
    }

    @Test
    public void test1302() throws Exception {
        String msg =
                "(002.yml): Error @ line: 2, col: 15. Invalid value type, expected: STRING, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'entryPoint' @ line: 2, col: 3\n" +
                        "\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/002.yml", msg);
    }

    @Test
    public void test1303() throws Exception {
        String msg =
                "(003.yml): Error @ line: 3, col: 16. Invalid value type, expected: ARRAY_OF_STRING, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'dependencies' @ line: 3, col: 3\n" +
                        "\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/003.yml", msg);
    }

    @Test
    public void test1304() throws Exception {
        String msg =
                "(004.yml): Error @ line: 4, col: 7. Invalid value type, expected: STRING, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'dependencies' @ line: 3, col: 3\n" +
                        "\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/004.yml", msg);
    }

    @Test
    public void test1305() throws Exception {
        String msg =
                "(005.yml): Error @ line: 6, col: 13. Invalid value type, expected: OBJECT, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'arguments' @ line: 6, col: 3\n" +
                        "\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/005.yml", msg);
    }

    @Test
    public void test1305_1() throws Exception {
        String msg =
                "(005_1.yml): Error @ line: 7, col: 7. Invalid value type, expected: NON_NULL, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'arguments' @ line: 6, col: 3\n" +
                        "\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/005_1.yml", msg);
    }

    @Test
    public void test1306() throws Exception {
        String msg =
                "(006.yml): Error @ line: 8, col: 9. Unknown options: ['trash' [NULL] @ line: 8, col: 9], expected: [activeProfiles, arguments, debug, dependencies, entryPoint, events, exclusive, meta, out, parallelLoopParallelism, processTimeout, requirements, runtime, suspendTimeout, template]. Remove invalid options and/or fix indentation\n" +
                        "\twhile processing steps:\n" +
                        "\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/006.yml", msg);
    }

    @Test
    public void test1307() throws Exception {
        String msg =
                "(007.yml): Error @ line: 8, col: 9. Invalid value type, expected: OBJECT, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'meta' @ line: 8, col: 3\n" +
                        "\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/007.yml", msg);
    }

    @Test
    public void test1308() throws Exception {
        String msg =
                "(008.yml): Error @ line: 8, col: 17. Invalid value type, expected: OBJECT, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'requirements' @ line: 8, col: 3\n" +
                        "\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/008.yml", msg);
    }

    @Test
    public void test1309() throws Exception {
        String msg =
                "(009.yml): Error @ line: 10, col: 19. Invalid value type, expected: ISO 8601 DURATION, got: STRING. Error info: Text cannot be parsed to a Duration\n" +
                        "\twhile processing steps:\n" +
                        "\t'processTimeout' @ line: 10, col: 3\n" +
                        "\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/009.yml", msg);
    }

    @Test
    public void test1310() throws Exception {
        String msg =
                "(010.yml): Error @ line: 11, col: 14. Invalid value type, expected: EXCLUSIVE_MODE, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'exclusive' @ line: 11, col: 3\n" +
                        "\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/010.yml", msg);
    }

    @Test
    public void test1311() throws Exception {
        String msg =
                "(011.yml): Error @ n/a. Mandatory parameter 'group' not found\n" +
                        "\twhile processing steps:\n" +
                        "\t'exclusive' @ line: 11, col: 3\n" +
                        "\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/011.yml", msg);
    }

    @Test
    public void test1311_1() throws Exception {
        String msg =
                "(011_1.yml): Error @ line: 4, col: 12. Invalid value type, expected: NON_EMPTY_STRING, got: STRING. Error info: Empty value\n" +
                        "\twhile processing steps:\n" +
                        "\t'group' @ line: 4, col: 5\n" +
                        "\t\t'exclusive' @ line: 2, col: 3\n" +
                        "\t\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/011_1.yml", msg);
    }

    @Test
    public void test1312() throws Exception {
        String msg =
                "(012.yml): Error @ line: 13, col: 12. Unknown options: ['mode1' [STRING] @ line: 13, col: 12], expected: [group, mode]. Remove invalid options and/or fix indentation\n" +
                        "\twhile processing steps:\n" +
                        "\t'exclusive' @ line: 11, col: 3\n" +
                        "\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/012.yml", msg);
    }

    @Test
    public void test1313() throws Exception {
        String msg =
                "(013.yml): Error @ line: 13, col: 11. Invalid value: canceL, expected: [cancel, cancelOld, wait]\n" +
                        "\twhile processing steps:\n" +
                        "\t'mode' @ line: 13, col: 5\n" +
                        "\t\t'exclusive' @ line: 11, col: 3\n" +
                        "\t\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/013.yml", msg);
    }

    @Test
    public void test1314() throws Exception {
        String msg =
                "(014.yml): Error @ line: 14, col: 11. Invalid value type, expected: EVENTS_CONFIGURATION, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'events' @ line: 14, col: 3\n" +
                        "\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/014.yml", msg);
    }

    @Test
    public void test1315() throws Exception {
        String msg =
                "(015.yml): Error @ line: 16, col: 22. Invalid value type, expected: ARRAY_OF_STRING, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'inVarsBlacklist' @ line: 16, col: 5\n" +
                        "\t\t'events' @ line: 14, col: 3\n" +
                        "\t\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/015.yml", msg);
    }

    @Test
    public void test1316() throws Exception {
        String msg =
                "(016.yml): Error @ line: 20, col: 24. Invalid value type, expected: BOOLEAN, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'recordTaskOutVars' @ line: 20, col: 5\n" +
                        "\t\t'events' @ line: 14, col: 3\n" +
                        "\t\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/016.yml", msg);
    }

    @Test
    public void test1317() throws Exception {
        String msg =
                "(017.yml): Error @ line: 21, col: 23. Invalid value type, expected: ARRAY_OF_STRING, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'outVarsBlacklist' @ line: 21, col: 5\n" +
                        "\t\t'events' @ line: 14, col: 3\n" +
                        "\t\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/017.yml", msg);
    }

    @Test
    public void test1318() throws Exception {
        String msg =
                "(018.yml): Error @ line: 26, col: 11. Unknown options: ['trash' [NULL] @ line: 26, col: 11], expected: [inVarsBlacklist, metaBlacklist, outVarsBlacklist, recordEvents, recordTaskInVars, recordTaskMeta, recordTaskOutVars, truncateInVars, truncateMaxArrayLength, truncateMaxDepth, truncateMaxStringLength, truncateMeta, truncateOutVars, updateMetaOnAllEvents]. Remove invalid options and/or fix indentation\n" +
                        "\twhile processing steps:\n" +
                        "\t'events' @ line: 14, col: 3\n" +
                        "\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/018.yml", msg);
    }

    @Test
    public void test1319() throws Exception {
        String msg =
                "(019.yml): Error @ line: 23, col: 8. Invalid value type, expected: ARRAY_OF_STRING, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'out' @ line: 23, col: 3\n" +
                        "\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/019.yml", msg);
    }

    @Test
    public void test1320() throws Exception {
        String msg =
                "(020.yml): Error @ line: 4, col: 5. Invalid value type, expected: STRING, got: ARRAY\n" +
                        "\twhile processing steps:\n" +
                        "\t'template' @ line: 3, col: 3\n" +
                        "\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/020.yml", msg);
    }

    @Test
    public void test1400() throws Exception {
        String msg =
                "(000.yml): Error @ line: 3, col: 10. Invalid value type, expected: EXPRESSION, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'if' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/if/000.yml", msg);
    }

    @Test
    public void test1401() throws Exception {
        String msg =
                "(001.yml): Error @ line: 3, col: 11. Invalid value type, expected: EXPRESSION, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'if' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/if/001.yml", msg);
    }

    @Test
    public void test1402() throws Exception {
        String msg =
                "(002.yml): Error @ n/a. Mandatory parameter 'then' not found\n" +
                        "\twhile processing steps:\n" +
                        "\t'if' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/if/002.yml", msg);
    }

    @Test
    public void test1403() throws Exception {
        String msg =
                "(003.yml): Error @ line: 4, col: 12. Invalid value type, expected: ARRAY_OF_STEP, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'then' @ line: 4, col: 7\n" +
                        "\t\t'if' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/if/003.yml", msg);
    }

    @Test
    public void test1404() throws Exception {
        String msg =
                "(004.yml): Error @ line: 6, col: 10. Unknown options: ['el' [NULL] @ line: 6, col: 10], expected: [else, meta, then]. Remove invalid options and/or fix indentation\n" +
                        "\twhile processing steps:\n" +
                        "\t'if' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/if/004.yml", msg);
    }

    @Test
    public void test1405() throws Exception {
        String msg =
                "(005.yml): Error @ line: 6, col: 12. Invalid value type, expected: ARRAY_OF_STEP, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'else' @ line: 6, col: 7\n" +
                        "\t\t'if' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/if/005.yml", msg);
    }

    @Test
    public void test1406() throws Exception {
        String msg =
                "(006.yml): Error @ line: 8, col: 13. Unknown options: ['trash' [NULL] @ line: 8, col: 13], expected: [else, meta, then]. Remove invalid options and/or fix indentation\n" +
                        "\twhile processing steps:\n" +
                        "\t'if' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/if/006.yml", msg);
    }

    @Test
    public void test1407() throws Exception {
        String msg =
                "(007.yml): Error @ line: 8, col: 12. Invalid value type, expected: OBJECT, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'meta' @ line: 8, col: 7\n" +
                        "\t\t'if' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/if/007.yml", msg);
    }

    @Test
    public void test1500() throws Exception {
        String msg =
                "(000.yml): Error @ line: 3, col: 14. Invalid value type, expected: EXPRESSION, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'switch' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/switch/000.yml", msg);
    }

    @Test
    public void test1501() throws Exception {
        String msg =
                "(001.yml): Error @ line: 3, col: 7. No branch labels defined\n" +
                        "\twhile processing steps:\n" +
                        "\t'switch' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/switch/001.yml", msg);
    }

    @Test
    public void test1502() throws Exception {
        String msg =
                "(002.yml): Error @ line: 4, col: 11. Invalid value type, expected: ARRAY_OF_STEP, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'switch' @ line: 3, col: 7\n" +
                        "\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/switch/002.yml", msg);
    }

    @Test
    public void test1503() throws Exception {
        String msg =
                "(003.yml): Error @ line: 6, col: 15. Invalid value type, expected: ARRAY_OF_STEP, got: NULL. Remove attribute or complete the definition\n" +
                        "\twhile processing steps:\n" +
                        "\t'default' @ line: 6, col: 7\n" +
                        "\t\t'switch' @ line: 3, col: 7\n" +
                        "\t\t\t'main' @ line: 2, col: 3\n" +
                        "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/switch/003.yml", msg);
    }

    @Test
    public void test1600() throws Exception {
        String msg = "(000.yml): Error @ line: 1, col: 13. Invalid value type, expected: ARRAY_OF_STRING, got: NULL. Remove attribute or complete the definition\n" +
                "\twhile processing steps:\n" +
                "\t'publicFlows' @ line: 1, col: 1";

        assertErrorMessage("errors/publicFlows/000.yml", msg);
    }

    @Test
    public void test1601() throws Exception {
        String msg = "(001.yml): Error @ line: 1, col: 14. Invalid value type, expected: ARRAY_OF_STRING, got: STRING\n" +
                "\twhile processing steps:\n" +
                "\t'publicFlows' @ line: 1, col: 1";

        assertErrorMessage("errors/publicFlows/001.yml", msg);
    }

    @Test
    public void test1602() throws Exception {
        String msg = "(002.yml): Error @ line: 2, col: 5. Invalid value type, expected: STRING, got: INT\n" +
                "\twhile processing steps:\n" +
                "\t'publicFlows' @ line: 1, col: 1";

        assertErrorMessage("errors/publicFlows/002.yml", msg);
    }

    @Test
    public void test1700() throws Exception {
        String msg = "(000.yml): Error @ line: 3, col: 14. Invalid value type, expected: STRING, got: NULL. Remove attribute or complete the definition\n" +
                "\twhile processing steps:\n" +
                "\t'script' @ line: 3, col: 7\n" +
                "\t\t'main' @ line: 2, col: 3\n" +
                "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/scripts/000.yml", msg);
    }

    @Test
    public void test1701() throws Exception {
        String msg = "(001.yml): Error @ line: 3, col: 15. Invalid value type, expected: STRING, got: INT\n" +
                "\twhile processing steps:\n" +
                "\t'script' @ line: 3, col: 7\n" +
                "\t\t'main' @ line: 2, col: 3\n" +
                "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/scripts/001.yml", msg);
    }

    @Test
    public void test1702() throws Exception {
        String msg = "(002.yml): Error @ line: 4, col: 14. Unknown options: ['body1' [STRING] @ line: 4, col: 14], expected: [body, error, in, loop, meta, name, out, parallelWithItems, retry, withItems]. Remove invalid options and/or fix indentation\n" +
                "\twhile processing steps:\n" +
                "\t'script' @ line: 3, col: 7\n" +
                "\t\t'main' @ line: 2, col: 3\n" +
                "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/scripts/002.yml", msg);
    }

    @Test
    public void test1703() throws Exception {
        String msg = "(003.yml): Error @ line: 7, col: 11. Invalid value type, expected: STEP, got: STRING\n" +
                "\twhile processing steps:\n" +
                "\t'error' @ line: 6, col: 7\n" +
                "\t\t'script' @ line: 3, col: 7\n" +
                "\t\t\t'main' @ line: 2, col: 3\n" +
                "\t\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/scripts/003.yml", msg);
    }

    @Test
    public void test1800() throws Exception {
        String msg = "(000.yml): Error @ line: 1, col: 11. Invalid value type, expected: RESOURCES, got: NULL. Remove attribute or complete the definition\n" +
                "\twhile processing steps:\n" +
                "\t'resources' @ line: 1, col: 1";

        assertErrorMessage("errors/resources/000.yml", msg);
    }

    @Test
    public void test1801() throws Exception {
        String msg = "(001.yml): Error @ line: 3, col: 5. Unknown options: ['trash' [ARRAY] @ line: 3, col: 5], expected: [concord]. Remove invalid options and/or fix indentation\n" +
                "\twhile processing steps:\n" +
                "\t'resources' @ line: 1, col: 1";

        assertErrorMessage("errors/resources/001.yml", msg);
    }

    @Test
    public void test1802() throws Exception {
        String msg = "(002.yml): Error @ line: 2, col: 12. Invalid value type, expected: ARRAY_OF_STRING, got: STRING\n" +
                "\twhile processing steps:\n" +
                "\t'concord' @ line: 2, col: 3\n" +
                "\t\t'resources' @ line: 1, col: 1";

        assertErrorMessage("errors/resources/002.yml", msg);
    }

    @Test
    public void test1900() throws Exception {
        String msg = "(000.yml): Error @ line: 3, col: 11. Invalid value type, expected: OBJECT, got: NULL. Remove attribute or complete the definition\n" +
                "\twhile processing steps:\n" +
                "\t'set' @ line: 3, col: 7\n" +
                "\t\t'main' @ line: 2, col: 3\n" +
                "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/setVariables/000.yml", msg);
    }

    @Test
    public void test1901() throws Exception {
        String msg = "(001.yml): Error @ line: 7, col: 9. Unknown options: ['meta1' [OBJECT] @ line: 7, col: 9], expected: [meta]. Remove invalid options and/or fix indentation\n" +
                "\twhile processing steps:\n" +
                "\t'set' @ line: 3, col: 7\n" +
                "\t\t'main' @ line: 2, col: 3\n" +
                "\t\t\t'flows' @ line: 1, col: 1";

        assertErrorMessage("errors/setVariables/001.yml", msg);
    }

    @Test
    public void test1902() throws Exception {
        String msg =
                "(021.yml): Error @ line: 23, col: 21. Invalid value type, expected: BOOLEAN, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'recordTaskMeta' @ line: 23, col: 5\n" +
                        "\t\t'events' @ line: 14, col: 3\n" +
                        "\t\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/021.yml", msg);
    }

    @Test
    public void test1903() throws Exception {
        String msg =
                "(022.yml): Error @ line: 24, col: 20. Invalid value type, expected: ARRAY_OF_STRING, got: INT\n" +
                        "\twhile processing steps:\n" +
                        "\t'metaBlacklist' @ line: 24, col: 5\n" +
                        "\t\t'events' @ line: 14, col: 3\n" +
                        "\t\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/022.yml", msg);
    }

    @Test
    public void test1904() throws Exception {
        String msg =
                "(023.yml): Error @ line: 11, col: 19. Invalid value type, expected: ISO 8601 DURATION, got: STRING. Error info: Text cannot be parsed to a Duration\n" +
                        "\twhile processing steps:\n" +
                        "\t'suspendTimeout' @ line: 11, col: 3\n" +
                        "\t\t'configuration' @ line: 1, col: 1";

        assertErrorMessage("errors/configuration/023.yml", msg);
    }

    private void assertErrorMessage(String resource, String expectedError) throws Exception {
        try {
            load(resource);
            fail("exception expected");
        } catch (YamlParserException e) {
            assertEquals(expectedError, e.getMessage());
        }
    }
}
