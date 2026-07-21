package com.freedom.remainz_v2.web.controller;

import java.util.Enumeration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.freedom.remainz_v2.common.exception.ApplicationInternalException;
import com.freedom.remainz_v2.common.util.MsgUtil;
import com.freedom.remainz_v2.web.service.RequestHandlingService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * URIパターンに基づいてDBレコード駆動でリクエストを処理するコントローラです。
 *
 * <p>
 * 移植元「remainz」の{@code ServiceControlServlet}に相当します。実際の業務ロジックの実行は
 * {@link RequestHandlingService}に委譲し、本クラスはリクエストコンテキストの構築とビュー解決のみを
 * 担当します。
 * </p>
 */
@Controller
public class RemainzV2Controller {

    private static final Logger logger = LoggerFactory.getLogger(RemainzV2Controller.class);

    private final RequestHandlingService requestHandlingService;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public RemainzV2Controller(RequestHandlingService requestHandlingService, ObjectMapper objectMapper,
            MsgUtil msg) {
        this.requestHandlingService = requestHandlingService;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @GetMapping("/remainz-v2/service/top.html")
    public String getTop(HttpServletRequest request, Model model) {
        return handleRequest(request, "GET", model);
    }

    @GetMapping("/remainz-v2/service/myPage.html")
    public String getMyPage(HttpServletRequest request, Model model) {
        return handleRequest(request, "GET", model);
    }

    @PostMapping("/remainz-v2/service/myPage.html")
    public String postMyPage(HttpServletRequest request, Model model) {
        return handleRequest(request, "POST", model);
    }

    private String handleRequest(HttpServletRequest request, String requestKind, Model model) {

        logRequestInfo(request);

        ObjectNode context = buildContext(request, requestKind);
        JsonNode result = readAsObjectNode(requestHandlingService.execute(writeAsString(context)));

        storeAccountIdIfExists(request.getSession(), result);
        populateModel(result, model);

        return resolveViewName(result);
    }

    private void logRequestInfo(HttpServletRequest request) {

        StringBuilder log = new StringBuilder();
        log.append("[Attributes]").append(System.lineSeparator());
        for (Enumeration<String> names = request.getAttributeNames(); names.hasMoreElements();) {
            String name = names.nextElement();
            log.append('\t').append(name).append(": ").append(request.getAttribute(name))
                    .append(System.lineSeparator());
        }

        log.append("[Headers]").append(System.lineSeparator());
        for (Enumeration<String> names = request.getHeaderNames(); names.hasMoreElements();) {
            String name = names.nextElement();
            log.append('\t').append(name).append(": ").append(request.getHeader(name))
                    .append(System.lineSeparator());
        }

        log.append("[Parameters]").append(System.lineSeparator());
        for (Enumeration<String> names = request.getParameterNames(); names.hasMoreElements();) {
            String name = names.nextElement();
            String value = "PASSWORD".equals(name) ? "*****" : request.getParameter(name);
            log.append('\t').append(name).append(": ").append(value).append(System.lineSeparator());
        }

        logger.info(log.toString());
    }

    private ObjectNode buildContext(HttpServletRequest request, String requestKind) {

        ObjectNode context = objectMapper.createObjectNode();

        for (Enumeration<String> names = request.getParameterNames(); names.hasMoreElements();) {
            String name = names.nextElement();
            context.put(name, request.getParameter(name));
        }

        String accountId = (String) request.getSession().getAttribute("accountId");
        if (accountId != null) {
            context.put("accountId", accountId);
        }

        context.put("requestKind", requestKind);
        context.put("requestUri", request.getRequestURI());
        context.put("sessionId", request.getSession().getId());

        return context;
    }

    private void storeAccountIdIfExists(HttpSession session, JsonNode result) {

        JsonNode account = result.path("account");
        if (!account.isArray() || account.isEmpty()) {
            return;
        }

        session.setAttribute("accountId", account.get(0).path("ACCNT_ID").asString());
    }

    private void populateModel(JsonNode result, Model model) {
        Map<String, Object> attributes = objectMapper.convertValue(result, new TypeReference<Map<String, Object>>() {
        });
        attributes.forEach(model::addAttribute);
    }

    private String resolveViewName(JsonNode result) {

        String respKind = result.path("respKind").asString("");
        String destination = result.path("destination").asString("");

        if ("redirect".equals(respKind)) {
            return "redirect:" + destination;
        }

        return destination.endsWith(".html") ? destination.substring(0, destination.length() - ".html".length())
                : destination;
    }

    private ObjectNode readAsObjectNode(String json) {
        try {
            return (ObjectNode) objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new ApplicationInternalException(msg.get("msg.err.web.jsonProcessingFailed", json), e);
        }
    }

    private String writeAsString(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException e) {
            throw new ApplicationInternalException(msg.get("msg.err.web.jsonProcessingFailed", node), e);
        }
    }
}
