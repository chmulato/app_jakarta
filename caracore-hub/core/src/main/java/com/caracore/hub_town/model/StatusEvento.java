package com.caracore.hub_town.model;

/**
 * Status dos eventos de integração.
 */
public enum StatusEvento {
    RECEBIDO,      // Evento recebido via webhook, aguardando processamento
    PROCESSANDO,   // Evento sendo processado
    PROCESSADO,    // Evento processado com sucesso
    ERRO,          // Erro durante o processamento
    IGNORADO       // Evento ignorado (duplicado ou irrelevante)
}