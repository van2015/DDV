/**
 * Contrato hexagonal: interfaces que el núcleo define y que los adaptadores
 * implementan.
 *
 * <ul>
 *   <li>{@code GitSourcePort} — acceso a repositorio y diffs (adaptador JGit)</li>
 *   <li>{@code ParserPort} — análisis AST (adaptador de parser)</li>
 *   <li>{@code RendererPort} — renderizado de diagramas (adaptador Mermaid)</li>
 *   <li>{@code StorePort} — persistencia de Sᵢ y diagramas (adaptador de archivo)</li>
 * </ul>
 *
 * <p>Esta capa apunta siempre hacia dentro: puede importar {@code domain},
 * nunca {@code adapters}.</p>
 */
package ddv.ports;
