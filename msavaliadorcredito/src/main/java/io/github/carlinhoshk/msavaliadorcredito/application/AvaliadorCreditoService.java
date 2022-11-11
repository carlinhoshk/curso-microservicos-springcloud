package io.github.carlinhoshk.msavaliadorcredito.application;

import feign.FeignException;
import io.github.carlinhoshk.msavaliadorcredito.application.ex.DadosClienteNotFoundException;
import io.github.carlinhoshk.msavaliadorcredito.application.ex.ErroComunicacaomicroservicesException;
import io.github.carlinhoshk.msavaliadorcredito.application.ex.ErroSolicitacaoCartaoException;
import io.github.carlinhoshk.msavaliadorcredito.domain.model.*;
import io.github.carlinhoshk.msavaliadorcredito.infra.clients.CartoesResourceClient;
import io.github.carlinhoshk.msavaliadorcredito.infra.clients.ClienteResourceClient;
import io.github.carlinhoshk.msavaliadorcredito.infra.clients.mqueue.SolicitacaoEmissaoCartaoPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AvaliadorCreditoService {

    private final ClienteResourceClient clientesClient;
    private final CartoesResourceClient cartoesCliente;
    private final SolicitacaoEmissaoCartaoPublisher emissaoCartaoPublisher; 

    public SituacaoCliente obterSituacaoCliente(String cpf) throws DadosClienteNotFoundException, ErroComunicacaomicroservicesException {
        try {
            ResponseEntity<DadosCliente> dadosClienteResponse = clientesClient.dadosCliente(cpf);
            ResponseEntity<List<CartaoCliente>> cartoesResponse = cartoesCliente.getCartoesByCliente(cpf);

            return SituacaoCliente
                    .builder()
                    .cliente(dadosClienteResponse.getBody())
                    .cartoes(cartoesResponse.getBody())
                    .build();
        } catch (FeignException.FeignClientException e) {
            int status = e.status();
            if (HttpStatus.NOT_FOUND.value() == status) {
                throw new DadosClienteNotFoundException();
            }
            throw new ErroComunicacaomicroservicesException(e.getMessage(), status);
        }
    }

    public RetornoAvaliacaoCliente realizarAvalaicao(String cpf, long renda)
            throws DadosClienteNotFoundException, ErroComunicacaomicroservicesException {
        try {
            ResponseEntity<DadosCliente> dadosClienteResponse = clientesClient.dadosCliente(cpf);
            ResponseEntity<List<Cartao>> cartoesResponse = cartoesCliente.getCartoesRendaAteh(renda);

            List<Cartao> cartoes = cartoesResponse.getBody();
            var ListaCartoesAprovados = cartoes.stream().map(cartao -> {

                DadosCliente dadosCliente = dadosClienteResponse.getBody();

                BigDecimal limiteBasico = cartao.getLimiteBasico();
                BigDecimal rendaBD = BigDecimal.valueOf(renda);
                BigDecimal idadeBD = BigDecimal.valueOf(dadosCliente.getIdade());
                var fator = idadeBD.divide((BigDecimal.valueOf(10)));
                BigDecimal limiteAprovado = fator.multiply(limiteBasico);

                CartaoAprovado aprovado = new CartaoAprovado();
                aprovado.setCartao(cartao.getNome());
                aprovado.setBandeira(cartao.getBandeira());
                aprovado.setLimiteAprovado(limiteAprovado);

                return aprovado;
            }).collect(Collectors.toList());
            return new RetornoAvaliacaoCliente(ListaCartoesAprovados);

        } catch (FeignException.FeignClientException e) {
            int status = e.status();
            if (HttpStatus.NOT_FOUND.value() == status) {
                throw new DadosClienteNotFoundException();
            }
            throw new ErroComunicacaomicroservicesException(e.getMessage(), status);
        }
    }

    public ProtocoloSolicitacaoCartao solicitarEmissaoCartao(DadosSolicitacaoEmissaoCartao dados){
        try {
            emissaoCartaoPublisher.solicitarCartao(dados);
            var protrocolo = UUID.randomUUID().toString();
            return new ProtocoloSolicitacaoCartao(protrocolo);
        } catch (Exception e) {
           throw new ErroSolicitacaoCartaoException(e.getMessage());
        }
    }
}