package com.freedom.remainz_v2.web.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freedom.remainz_v2.common.util.MsgUtil;
import com.freedom.remainz_v2.web.service.RequestHandlingService;

/**
 * {@link RemainzV2Controller}のテストです。
 */
@WebMvcTest(RemainzV2Controller.class)
class RemainzV2ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestHandlingService requestHandlingService;

    @MockitoBean
    private MsgUtil msg;

    @Test
    void トップページのGETリクエストで応答種別forwardの場合はビュー名が拡張子無しで解決されること() throws Exception {

        when(requestHandlingService.execute(anyString())).thenReturn(
                "{\"respKind\":\"forward\",\"destination\":\"10000_contents.html\","
                        + "\"htmlPage\":[{\"partsInPageId\":\"1000001\",\"items\":[]}]}");

        mockMvc.perform(get("/remainz-v2/service/top.html"))
                .andExpect(status().isOk())
                .andExpect(view().name("10000_contents"))
                .andExpect(model().attribute("respKind", "forward"))
                .andExpect(model().attributeExists("htmlPage"));
    }

    @Test
    void 応答種別がredirectの場合はredirectプレフィックス付きのビュー名が返却されること() throws Exception {

        when(requestHandlingService.execute(anyString()))
                .thenReturn("{\"respKind\":\"redirect\",\"destination\":\"top.html\"}");

        mockMvc.perform(get("/remainz-v2/service/top.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:top.html"));
    }
}
