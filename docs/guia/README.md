# Guia do ExtPulse

Um capítulo por passo do projeto: o que foi feito, porquê, e como confirmar que
funciona. Lê-se por ordem. As decisões técnicas (curtas, em inglês) estão em
[`../decisions`](../decisions).

**[Ferramentas e registo](ferramentas.md):** o que foi feito em cada passo e porquê,
os problemas que apareceram e uma ficha por ferramenta. Atualiza-se no fim de cada passo.

| Passo | Capítulo | Estado |
| --- | --- | --- |
| 1 | [Setup do projeto](01-setup.md) | feito |
| 2 | [Esquema da base de dados](02-esquema.md) | feito |
| 3 | [Adaptadores das lojas](03-lojas.md) | feito |
| 4 | [Recolha diária](04-recolha.md) | feito |
| 5 | [Contas e login (Spring Security, JWT)](05-contas.md) | feito |
| 6 | [Catálogo e estatísticas na API](06-catalogo-estatisticas.md) | feito |
| 7 | [Avaliações](07-avaliacoes.md) | feito |
| 8 | [Alertas (email e Discord) e resumo semanal](08-alertas.md) | feito |
| 9 | Página pública e badge | por fazer |
| 10 | Deploy | a seguir |

A ordem mudou no passo 5: as contas vieram antes das estatísticas, porque tudo em
`/api` pertence a uma conta (ver o [capítulo 5](05-contas.md)).
