package kr.ac.knue.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:faculty_evaluation;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.locations=classpath:db/migration-h2"
})
@AutoConfigureMockMvc
class CommonApiContractTest {
    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);

    @Autowired MockMvc mvc;

    @Test
    void openapi_fixture_is_available_from_classpath() {
        assertThat(new ClassPathResource("contracts/openapi.yaml").exists()).isTrue();
    }

    @Test
    void get_health_returns_api_envelope_contract() throws Exception {
        mvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta.timestamp").exists())
            .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void post_auth_login_validates_credentials_and_creates_active_session_side_effect() throws Exception {
        MvcResult login = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"loginId":"admin","password":"admin"}
                    """))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.sessionId").isNotEmpty())
            .andReturn();

        String session = login.getResponse().getCookie("SESSION").getValue();
        mvc.perform(get("/api/auth/me").cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.loginId").value("admin"))
            .andExpect(jsonPath("$.data.roleCodes[0]").value("R09"));

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.fieldErrors.loginId").exists());

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"loginId":"admin","password":"wrong"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIAL"));
    }

    @Test
    void post_auth_logout_requires_authentication_and_expires_session_side_effect() throws Exception {
        String session = loginSession("admin");

        mvc.perform(post("/api/auth/logout").cookie(sessionCookie(session)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk())
            .andExpect(cookie().maxAge("SESSION", 0))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.loggedOut").value(true));

        mvc.perform(get("/api/auth/me").cookie(sessionCookie(session)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void get_admin_users_supports_portal_filtering_and_auth_contract() throws Exception {
        String session = loginSession("admin");

        mvc.perform(get("/api/admin/users?filter=ADMIN-001").cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].userId").value("admin-user"))
            .andExpect(jsonPath("$.data[0].employeeNo").value("ADMIN-001"))
            .andExpect(jsonPath("$.data[0].organizationName").exists());

        mvc.perform(get("/api/admin/users"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void patch_admin_users_user_id_updates_enabled_flag_and_manual_roles_side_effect() throws Exception {
        String adminSession = loginSession("admin");
        String professorSession = loginSession("professor");

        mvc.perform(patch("/api/admin/users/prof-user")
                .cookie(sessionCookie(professorSession))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"systemEnabled":"Y","roleCodes":["R01"]}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mvc.perform(patch("/api/admin/users/prof-user")
                .cookie(sessionCookie(adminSession))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"systemEnabled":"N","roleCodes":["R01"]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updated").value(true));

        mvc.perform(get("/api/admin/users?filter=PROF-001").cookie(sessionCookie(adminSession)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].systemEnabled").value("N"))
            .andExpect(jsonPath("$.data[0].roleCodes").value("R01"));

        mvc.perform(patch("/api/admin/users/prof-user")
                .cookie(sessionCookie(adminSession))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"systemEnabled":"Y","roleCodes":["R01"]}
                    """))
            .andExpect(status().isOk());

        mvc.perform(patch("/api/admin/users/unknown-user")
                .cookie(sessionCookie(adminSession))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"systemEnabled":"Y","roleCodes":["R01"]}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fieldErrors.userId").exists());
    }

    @Test
    void get_admin_organizations_supports_portal_filtering_contract() throws Exception {
        String session = loginSession("admin");

        mvc.perform(get("/api/admin/organizations").cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].orgCode").exists());

        mvc.perform(get("/api/admin/organizations?filter=컴퓨터").cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].orgCode").value("CSE"))
            .andExpect(jsonPath("$.data[0].orgName").value("컴퓨터교육과"));
    }

    @Test
    void get_admin_organizations_tree_returns_hierarchy_contract() throws Exception {
        String session = loginSession("admin");

        mvc.perform(get("/api/admin/organizations/tree").cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].orgCode").value("KNUE"))
            .andExpect(jsonPath("$.data[0].children").isArray());
    }

    @Test
    void put_admin_organizations_relationship_validates_date_and_persists_state_transition() throws Exception {
        String session = loginSession("admin");

        mvc.perform(put("/api/admin/organizations/CSE/relationship")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"parentOrgCode":"EDU","effectiveStartDate":"2026-12-31","effectiveEndDate":"2026-01-01"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fieldErrors.effectiveEndDate").exists());

        mvc.perform(put("/api/admin/organizations/CSE/relationship")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"parentOrgCode":"EDU","effectiveStartDate":"2026-01-01","effectiveEndDate":"2026-12-31"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updated").value(true));

        mvc.perform(get("/api/admin/organizations/tree").cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].children").isArray());

        mvc.perform(put("/api/admin/organizations/CSE/relationship")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"parentOrgCode":"EDU","effectiveStartDate":"2026-01-01"}
                    """))
            .andExpect(status().isOk());
    }

    @Test
    void get_admin_roles_and_put_admin_roles_role_code_cover_business_validation_and_side_effect() throws Exception {
        String session = loginSession("admin");

        mvc.perform(get("/api/admin/roles").cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[8].roleCode").value("R09"));

        mvc.perform(put("/api/admin/roles/R01")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"roleCode":"R02","roleName":"교원","purpose":"목적","useYn":"Y"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fieldErrors.roleCode").exists());

        mvc.perform(put("/api/admin/roles/R01")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"roleName":"교원 수정","purpose":"본인 업무","grantCriteria":"재직","dataScopeDefault":"본인","useYn":"Y"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updated").value(true));

        mvc.perform(get("/api/admin/roles").cookie(sessionCookie(session)))
            .andExpect(jsonPath("$.data[0].roleCode").value("R01"))
            .andExpect(jsonPath("$.data[0].grantCriteria").value("재직"))
            .andExpect(jsonPath("$.data[0].dataScopeDefault").value("본인"));
    }

    @Test
    void get_admin_user_roles_and_put_admin_users_user_id_roles_persist_assignments_with_state() throws Exception {
        String adminSession = loginSession("admin");
        String professorSession = loginSession("professor");

        mvc.perform(get("/api/admin/user-roles?filter=PROF-001").cookie(sessionCookie(adminSession)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].assignmentType").exists())
            .andExpect(jsonPath("$.data[0].status").exists());

        mvc.perform(put("/api/admin/users/prof-user/roles")
                .cookie(sessionCookie(professorSession))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assignments":[{"roleCode":"R01","assignmentType":"MANUAL","effectiveStartDate":"2026-01-01","status":"ACTIVE"}]}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mvc.perform(put("/api/admin/users/prof-user/roles")
                .cookie(sessionCookie(adminSession))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assignments":[{"roleCode":"R01","assignmentType":"MANUAL","effectiveStartDate":"2026-01-01","effectiveEndDate":"2026-12-31","approverUserId":"admin-user","status":"REVOKED"}]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updated").value(true));

        mvc.perform(get("/api/admin/user-roles?filter=PROF-001").cookie(sessionCookie(adminSession)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].roleCode").value("R01"))
            .andExpect(jsonPath("$.data[0].approverUserId").value("admin-user"))
            .andExpect(jsonPath("$.data[0].status").value("REVOKED"));

        mvc.perform(put("/api/admin/users/prof-user/roles")
                .cookie(sessionCookie(adminSession))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assignments":[{"roleCode":"R99","assignmentType":"MANUAL","effectiveStartDate":"2026-01-01","status":"ACTIVE"}]}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fieldErrors.roleCode").exists());
    }

    @Test
    void get_and_put_admin_menu_permissions_filter_and_reflect_navigation_side_effect() throws Exception {
        String session = loginSession("admin");

        mvc.perform(get("/api/admin/menu-permissions?targetType=ROLE&targetId=R09").cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].targetType").value("ROLE"))
            .andExpect(jsonPath("$.data[0].allowAccess").exists());

        mvc.perform(put("/api/admin/menu-permissions")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"targetType":"INVALID","targetId":"R09","permissions":[]}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fieldErrors.targetType").exists());

        mvc.perform(put("/api/admin/menu-permissions")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"targetType":"ROLE","targetId":"R09","permissions":[{"menuId":"M-USERS","allowAccess":"Y"}]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updated").value(true));

        mvc.perform(get("/api/admin/menu-permissions?targetType=ROLE&targetId=R09").cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.menuId == 'M-USERS')].allowAccess").value("Y"));

        mvc.perform(get("/api/admin/navigation").cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].children").isArray());
    }

    @Test
    void get_admin_menus_tree_and_put_admin_menus_menu_id_structure_persist_parent_side_effect() throws Exception {
        String session = loginSession("admin");

        mvc.perform(get("/api/admin/menus/tree").cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].menuId").value("M-SYS"))
            .andExpect(jsonPath("$.data[0].children").isArray());

        mvc.perform(put("/api/admin/menus/M-USERS/structure")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"parentMenuId":"M-SYS","menuLevel":4}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fieldErrors.menuLevel").exists());

        mvc.perform(put("/api/admin/menus/M-USERS/structure")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"parentMenuId":"M-USER-ORG","menuLevel":3}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updated").value(true));
    }

    @Test
    void patch_admin_menus_order_validates_orders_and_persists_sort_order_side_effect() throws Exception {
        String session = loginSession("admin");

        mvc.perform(patch("/api/admin/menus/order")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fieldErrors.orders").exists());

        mvc.perform(patch("/api/admin/menus/order")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"orders":[{"menuId":"M-USERS","sortOrder":11},{"menuId":"M-ORGS","sortOrder":12}]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updated").value(true));

        mvc.perform(get("/api/admin/menus").cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.menuId == 'M-USERS')].sortOrder").value(11));
    }

    @Test
    void get_post_and_put_admin_menus_cover_execution_info_contract_and_side_effects() throws Exception {
        String session = loginSession("admin");
        String menuName = "계약테스트메뉴-" + RUN_ID;

        mvc.perform(get("/api/admin/menus").cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].screenId").exists())
            .andExpect(jsonPath("$.data[0].businessCategory").exists());

        mvc.perform(post("/api/admin/menus")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fieldErrors.menuName").exists());

        mvc.perform(post("/api/admin/menus")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"menuName":"%s","screenId":"SCR-%s","url":"/admin/contract-%s","businessCategory":"COMMON","useYn":"Y"}
                    """.formatted(menuName, RUN_ID, RUN_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.created").value(true));

        mvc.perform(get("/api/admin/menus").cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.menuName == '%s')].screenId".formatted(menuName)).value("SCR-" + RUN_ID));

        mvc.perform(put("/api/admin/menus/M-USERS")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"menuName":"사용자 관리","screenId":"SCR-USERS","url":"/admin/users","businessCategory":"COMMON","useYn":"Y"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updated").value(true));

        mvc.perform(put("/api/admin/menus/unknown-menu")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"menuName":"없는 메뉴","screenId":"SCR-NONE","url":"/admin/none","businessCategory":"COMMON","useYn":"Y"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fieldErrors.menuId").exists());
    }

    @Test
    void get_post_and_put_admin_code_groups_cover_business_and_side_effects() throws Exception {
        String session = loginSession("admin");
        String groupId = "CONTRACT_" + RUN_ID.toUpperCase();

        mvc.perform(get("/api/admin/code-groups?filter=USE_YN").cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].groupId").value("USE_YN"));

        mvc.perform(post("/api/admin/code-groups")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fieldErrors.groupId").exists());

        mvc.perform(post("/api/admin/code-groups")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"groupId":"%s","groupName":"계약테스트그룹","description":"생성 검증","managingDepartment":"교수지원과","useYn":"Y"}
                    """.formatted(groupId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.created").value(true));

        mvc.perform(put("/api/admin/code-groups/USE_YN")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"groupName":"사용여부 수정","description":"수정 검증","managingDepartment":"교수지원과","useYn":"Y"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updated").value(true));

        mvc.perform(get("/api/admin/code-groups?filter=USE_YN").cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].groupName").value("사용여부 수정"));

        mvc.perform(put("/api/admin/code-groups/unknown-group")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"groupName":"없는 그룹","description":"없음","managingDepartment":"교수지원과","useYn":"Y"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fieldErrors.groupId").exists());
    }

    @Test
    void get_post_and_put_admin_code_details_cover_parent_and_extra_attribute_side_effects() throws Exception {
        String session = loginSession("admin");
        String groupId = "DETAIL_" + RUN_ID.toUpperCase();

        mvc.perform(post("/api/admin/code-groups")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"groupId":"%s","groupName":"상세테스트그룹","description":"생성 검증","managingDepartment":"교수지원과","useYn":"Y"}
                    """.formatted(groupId)))
            .andExpect(status().isOk());

        mvc.perform(get("/api/admin/code-details?groupId=USE_YN").cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].codeValue").exists())
            .andExpect(jsonPath("$.data[0].extraAttributes").exists());

        mvc.perform(post("/api/admin/code-details")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fieldErrors.groupId").exists());

        mvc.perform(post("/api/admin/code-details")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"groupId":"%s","codeValue":"PARENT","codeName":"상위","sortOrder":1,"extraAttributes":"{\\"level\\":1}","useYn":"Y"}
                    """.formatted(groupId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.created").value(true));

        mvc.perform(post("/api/admin/code-details")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"groupId":"%s","codeValue":"CHILD","codeName":"하위","parentCodeValue":"PARENT","sortOrder":2,"extraAttributes":"{\\"level\\":2}","useYn":"Y"}
                    """.formatted(groupId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.created").value(true));

        mvc.perform(get("/api/admin/code-details?groupId=" + groupId).cookie(sessionCookie(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.codeValue == 'CHILD')].parentCodeValue").value("PARENT"));

        mvc.perform(put("/api/admin/code-details/CD-USE-Y")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"groupId":"USE_YN","codeValue":"Y","codeName":"사용","sortOrder":1,"extraAttributes":"{\\"boolean\\":true,\\"contract\\":true}","useYn":"Y"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updated").value(true));

        mvc.perform(put("/api/admin/code-details/unknown-detail")
                .cookie(sessionCookie(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"groupId":"USE_YN","codeValue":"Z","codeName":"없음","sortOrder":99,"extraAttributes":"{}","useYn":"Y"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fieldErrors.detailId").exists());
    }

    private String loginSession(String loginId) throws Exception {
        MvcResult login = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"loginId":"%s","password":"admin"}
                    """.formatted(loginId)))
            .andExpect(status().isOk())
            .andReturn();
        return login.getResponse().getCookie("SESSION").getValue();
    }

    private Cookie sessionCookie(String session) {
        return new Cookie("SESSION", session);
    }
}