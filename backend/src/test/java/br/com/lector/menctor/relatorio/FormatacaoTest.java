package br.com.lector.menctor.relatorio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FormatacaoTest {

    /** Valores esperados obtidos com {@code f"{valor:.2f}"} no Python. */
    @ParameterizedTest
    @CsvSource({
            "2.675, 2.67", "0.125, 0.12", "0.375, 0.38", "2.345, 2.35", "1.005, 1.00",
            "3.995, 4.00", "2.665, 2.67", "3.12, 3.12", "3, 3.00", "0, 0.00", "-0.5, -0.50", "1.665, 1.67"})
    void duasCasasComoOPython(double valor, String esperado) {
        assertEquals(esperado, RelatorioPsicossocialPdf.fmt2(valor));
    }

    @Test
    void faixasDeRiscoDoCopsoq() {
        assertEquals("ALTO", RelatorioPsicossocialPdf.nivel(2.67));
        assertEquals("MODERADO", RelatorioPsicossocialPdf.nivel(2.669));
        assertEquals("MODERADO", RelatorioPsicossocialPdf.nivel(1.67));
        assertEquals("BAIXO", RelatorioPsicossocialPdf.nivel(1.669));
    }
}
