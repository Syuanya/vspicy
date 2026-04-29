package com.vspicy.auth.service;

/**
 * Deprecated placeholder.
 *
 * Phase19 曾新增 JwtTokenService，但项目中已存在：
 * com.vspicy.auth.security.JwtTokenService
 *
 * 两个类的默认 Spring Bean 名都会是 jwtTokenService，导致 AuthApplication 启动时报：
 * ConflictingBeanDefinitionException。
 *
 * 新的 JWT 签发服务已改名为 AuthJwtTokenService。
 * 这个类不再加 @Service，避免参与 Spring Bean 扫描。
 */
@Deprecated
public class JwtTokenService {
}
