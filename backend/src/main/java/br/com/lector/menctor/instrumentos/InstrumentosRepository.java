package br.com.lector.menctor.instrumentos;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.lector.menctor.dados.Banco;

@ApplicationScoped
public class InstrumentosRepository {

    @Inject
    Banco banco;

    public List<ObjectNode> listarInstrumentos() {
        return banco.transacao(c -> banco.consultar(c, """
                select i.*,
                       count(distinct d.id) as total_dimensoes,
                       count(distinct q.id) as total_questoes
                  from instrumentos i
                  left join instrumento_dimensoes d on d.instrumento_id = i.id and d.ativo = true
                  left join instrumento_questoes q on q.dimensao_id = d.id and q.ativo = true
                 group by i.id
                 order by i.nome"""));
    }

    public Optional<ObjectNode> detalharInstrumento(String id) {
        return banco.transacao(c -> {
            Optional<ObjectNode> optInst = banco.consultarUm(c, "select * from instrumentos where id = ?", id);
            if (optInst.isEmpty()) {
                return Optional.empty();
            }
            ObjectNode inst = optInst.get();
            ArrayNode arrDimensoes = inst.putArray("dimensoes");

            List<ObjectNode> dimensoes = banco.consultar(c,
                    "select * from instrumento_dimensoes where instrumento_id = ? order by ordem, nome", id);

            for (ObjectNode dim : dimensoes) {
                String dimId = dim.get("id").asText();
                ArrayNode arrQuestoes = dim.putArray("questoes");
                banco.consultar(c, "select * from instrumento_questoes where dimensao_id = ? order by ordem", dimId)
                        .forEach(arrQuestoes::add);
                arrDimensoes.add(dim);
            }
            return Optional.of(inst);
        });
    }

    public List<ObjectNode> listarDimensoes(String instrumentoId) {
        return banco.transacao(c -> banco.consultar(c,
                "select * from instrumento_dimensoes where instrumento_id = ? order by ordem, nome", instrumentoId));
    }

    public List<ObjectNode> listarQuestoes(String dimensaoId) {
        return banco.transacao(c -> banco.consultar(c,
                "select * from instrumento_questoes where dimensao_id = ? order by ordem", dimensaoId));
    }

    public Optional<ObjectNode> toggleQuestao(String id, boolean ativo) {
        return banco.transacao(c -> {
            banco.executar(c, "update instrumento_questoes set ativo = ? where id = ?", ativo, id);
            return banco.consultarUm(c, "select * from instrumento_questoes where id = ?", id);
        });
    }

    public Optional<ObjectNode> toggleDimensao(String id, boolean ativo) {
        return banco.transacao(c -> {
            banco.executar(c, "update instrumento_dimensoes set ativo = ? where id = ?", ativo, id);
            return banco.consultarUm(c, "select * from instrumento_dimensoes where id = ?", id);
        });
    }
}
