package com.skillbridge.ai.service.agente;
/** Defensa adicional; la frontera principal es la proyección autorizada antes del LLM. */
public final class SalidaAgente {
 private SalidaAgente(){}
 public static String limpiar(String texto){
  if(texto==null)return "";
  String t=texto.replaceAll("(?i)(sk-[a-z0-9_-]{12,}|Bearer\\s+[a-z0-9._-]+|eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+|\\$2[aby]\\$\\d{2}\\$[./a-zA-Z0-9]{53})","[secreto omitido]");
  t=t.replaceAll("(?i)(password|api[_-]?key|token|secret)\\s*[:=]\\s*[^\\s,;]+","$1=[omitido]");
  return t.length()>18000?t.substring(0,18000)+" [resultado limitado]":t;
 }
}
