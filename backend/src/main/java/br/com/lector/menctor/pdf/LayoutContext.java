package br.com.lector.menctor.pdf;

/** O que um elemento pode consultar do documento ao se dividir. */
public interface LayoutContext {

    Frame currentFrame();

    /** Quadro que será usado depois do atual (na próxima página, com o próximo modelo). */
    Frame peekNextFrame();
}
