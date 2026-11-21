package ddv.ports;

/**
 * Handle opaco a un repositorio abierto.
 *
 * <p>El dominio solo sabe que es cerrable: la naturaleza real (JGit, etc.)
 * vive exclusivamente en el adaptador — evita filtrar la infraestructura
 * hacia el núcleo.</p>
 */
public interface RepositoryHandle extends AutoCloseable {

    @Override
    void close();
}
