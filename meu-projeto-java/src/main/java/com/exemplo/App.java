package com.exemplo;

/**
 * Classe principal da aplicação
 */
public class App {
    public static void main(String[] args) {
        System.out.println("Olá, Mundo! Bem-vindo ao meu projeto Java com Maven.");
    }

    /**
     * Método de exemplo que retorna uma saudação
     * @param nome Nome da pessoa a ser saudada
     * @return Uma mensagem de saudação personalizada
     */
    public String saudar(String nome) {
        return "Olá, " + nome + "! Bem-vindo ao meu projeto Java.";
    }
}