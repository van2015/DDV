/**
 * Pipeline interno del bucle UC2 → UC3 → UC4.
 *
 * <p>Etapas: {@code DiffExtractor} (Δ) → {@code StructuralAnalyzer} (AST → Sᵢ)
 * → {@code DiagramGenerator} (UML). Cada etapa depende solo de puertos.</p>
 */
package ddv.application.pipeline;
