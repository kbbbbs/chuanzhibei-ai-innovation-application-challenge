package com.cupk.aiacademy.rag;

/** Immutable identity snapshot. Authorization rechecks current account and role before using it. */
public record RagActor(Integer userId, String roleCode) { }
