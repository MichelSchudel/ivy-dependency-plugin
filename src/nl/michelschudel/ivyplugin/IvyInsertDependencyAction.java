package nl.michelschudel.ivyplugin;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.indices.MavenArtifactSearchDialog;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.model.MavenId;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

public class IvyInsertDependencyAction extends AnAction {

    private static final String DEPENDENCIES_END_ELEMENT = "</dependencies>";
    private static final String IVY_MODULE_END_ELEMENT = "</ivy-module>";

    @Override
    public void update(AnActionEvent e) {
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        boolean visible = file != null && file.getName().contains("ivy.xml");
        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        MavenProjectIndicesManager instance = MavenProjectIndicesManager.getInstance(e.getProject());
        instance.scheduleUpdateIndicesList(mavenIndices -> MavenIndicesManager.getInstance().getIndices());
        instance.initComponent();
        List<MavenId> mavenIds = MavenArtifactSearchDialog.searchForClass(e.getProject(), null);
        if (mavenIds != null && mavenIds.size() > 0) {
            final PsiFile file = CommonDataKeys.PSI_FILE.getData(e.getDataContext());
            writeDependencyToFile(project, mavenIds, file);
        }
    }

    private void writeDependencyToFile(final Project project, final List<MavenId> mavenIds, final PsiFile file) {
        new WriteCommandAction(project, "Add Maven Dependency", file) {
            @Override
            protected void run(@NotNull Result result) throws Throwable {
                boolean isTestSource = false;
                VirtualFile virtualFile = file.getOriginalFile().getVirtualFile();
                if (virtualFile != null) {
                    isTestSource = ProjectRootManager.getInstance(project).getFileIndex().isInTestSourceContent(virtualFile);
                }
                virtualFile.setWritable(true);
                byte[] content = virtualFile.contentsToByteArray();
                String s = new String(content);
                StringBuilder stringBuilder = new StringBuilder(s);
                writeDependeciesBlockIfNeeded(stringBuilder);
                int index = stringBuilder.indexOf(DEPENDENCIES_END_ELEMENT);
                stringBuilder.insert(index, buildDependency(mavenIds));
                virtualFile.setBinaryContent(stringBuilder.toString().getBytes());

            }
        }.execute();
    }

    private void writeDependeciesBlockIfNeeded(StringBuilder stringBuilder) {
        int index = stringBuilder.indexOf(DEPENDENCIES_END_ELEMENT);
        if (index < 0) {
            int indexOfRoot = stringBuilder.indexOf(IVY_MODULE_END_ELEMENT);
            if (indexOfRoot >= 0) {
                stringBuilder.insert(indexOfRoot, "<dependencies></dependencies>");
            }
        }
    }

    private char[] buildDependency(final List<MavenId> mavenIds) {
        MavenId mavenId = mavenIds.get(0);
        String groupId = mavenId.getGroupId();
        String artifactId = mavenId.getArtifactId();
        String version = mavenId.getVersion();
        return String.format("<dependency org=\"%s\" name=\"%s\" rev=\"%s\"/>", groupId, artifactId, version).toCharArray();

    }
}
