## Visão Geral

O `SimpleHttpClient` é uma biblioteca Java projetada para facilitar a realização de chamadas HTTP com recursos avançados de resiliência, como retry (tentativas repetidas) e circuit breaker (interrupção de chamadas sob condições específicas para prevenir falhas em cascata). Esta biblioteca visa oferecer uma solução simples e eficaz para integrar padrões de resiliência em aplicações que realizam operações de rede frequentes, melhorando a robustez e a confiabilidade da comunicação entre serviços.

## Problema Resolvido

Em um ambiente de microserviços ou em qualquer sistema distribuído, as chamadas de rede são inevitáveis e estão sujeitas a falhas por várias razões, como latência, sobrecarga do servidor, interrupções temporárias de rede, etc. Sem uma estratégia de resiliência adequada, essas falhas podem se propagar e afetar a estabilidade e disponibilidade do sistema como um todo.

O `SimpleHttpClient` aborda esses problemas oferecendo:

- **Retry**: Automatiza a tentativa de repetição de chamadas falhas sob condições configuráveis, ajudando a superar falhas transitórias sem a necessidade de intervenção manual ou lógica complexa de retry no lado do cliente.
- **Circuit Breaker**: Previne a sobrecarga de um serviço que está enfrentando falhas, interrompendo temporariamente as chamadas para esse serviço quando um limite de falhas é atingido, permitindo que ele se recupere.

## Por Que Usar?

- **Simplicidade**: Facilita a implementação de padrões de resiliência sem a necessidade de uma grande quantidade de código boilerplate ou a dependência de frameworks pesados.
- **Configurável**: Oferece flexibilidade para configurar parâmetros de retry e circuit breaker de acordo com as necessidades específicas de cada aplicação.
- **Robustez**: Melhora a robustez das chamadas HTTP em sua aplicação, tratando automaticamente condições comuns de falha e promovendo uma arquitetura mais resiliente.
- **Desempenho**: Projetado com o desempenho em mente, minimizando o impacto no tempo de resposta da aplicação através de um gerenciamento eficiente de retries e circuit breakers.

## Começando

Para começar a usar o `SimpleHttpClient` em seu projeto, siga estes passos:

1. **Inclusão da Biblioteca**: Adicione a biblioteca ao seu projeto. (Detalhes de como adicionar dependem do gerenciador de pacotes ou do sistema de build que você está usando.)

2. **Configuração**: Configure o `SimpleHttpClient` com as políticas desejadas de retry e circuit breaker:

    ```java
    SimpleHttpClient client = new SimpleHttpClient()
        .withRetryPolicy(retryConfig)
        .withCircuitBreaker(circuitBreakerConfig)
        .withTimeout(Duration.ofSeconds(5)); // Configura um timeout opcional
    ```

3. **Realização de Chamadas HTTP**: Use o cliente para fazer chamadas HTTP, aproveitando as políticas de resiliência configuradas:

    ```java
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://example.com"))
        .GET()
        .build();

    try {
        HttpResponse<String> response = client.makeRequest(request);
        // Processa a resposta
    } catch (Exception e) {
        // Trata exceções, incluindo falhas após retries ou quando o circuit breaker está aberto
    }
    ```
