package com.caracore.hub_town.model;

/**
 * Canais de origem dos pedidos no Hub.
 * Fase 1: MANUAL
 * Fase 2+: ML (Mercado Livre), SHOPEE, B2W, AMAZON
 */
public enum CanalPedido {
    MANUAL,
    ML,      // Mercado Livre
    SHOPEE,  // Shopee
    B2W,     // B2W (Americanas, Submarino, etc.)
    AMAZON   // Amazon
}
