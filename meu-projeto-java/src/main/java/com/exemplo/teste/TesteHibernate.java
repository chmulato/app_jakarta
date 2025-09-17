package com.exemplo.teste;

import org.hibernate.annotations.common.reflection.ReflectionManager;

/**
 * Classe para testar se a dependência hibernate-commons-annotations está funcionando
 */
public class TesteHibernate {
    
    public static void main(String[] args) {
        System.out.println("Iniciando teste do Hibernate Commons Annotations...");
        
        try {
            // Apenas verificando se a classe existe e pode ser carregada
            Class<?> cls = Class.forName("org.hibernate.annotations.common.reflection.ReflectionManager");
            System.out.println("✅ Classe ReflectionManager carregada com sucesso: " + cls.getName());
            
            // Verificando o pacote
            Package pkg = ReflectionManager.class.getPackage();
            System.out.println("✅ Pacote: " + pkg.getName());
            System.out.println("✅ Versão: " + pkg.getImplementationVersion());
            
            System.out.println("Teste concluído com sucesso! A dependência hibernate-commons-annotations está funcionando.");
        } catch (ClassNotFoundException e) {
            System.out.println("❌ ERRO: A classe ReflectionManager não foi encontrada!");
            System.out.println("❌ Detalhes: " + e.getMessage());
            e.printStackTrace();
        } catch (NoClassDefFoundError e) {
            System.out.println("❌ ERRO: A definição da classe ReflectionManager não foi encontrada!");
            System.out.println("❌ Detalhes: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("❌ ERRO: Ocorreu um erro desconhecido!");
            System.out.println("❌ Detalhes: " + e.getMessage());
            e.printStackTrace();
        }
    }
}