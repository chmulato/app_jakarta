package com.caracore.hub_town.servlet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Testes básicos do DashboardServlet")
class DashboardServletTest {
    private DashboardServlet dashboardServlet;

    @BeforeEach
    void setUp() {
        dashboardServlet = new DashboardServlet();
    }

    @Test
    @DisplayName("Deve criar instância do DashboardServlet")
    void deveCriarInstanciaDoServlet() {
        assertThat(dashboardServlet).isNotNull();
    }
}
