package dev.engineeringlab.stratify.structure.scanner.rule;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MS-020: Bounded Context Scope.
 *
 * <p>Detects excessive .getParent() chains that reach outside bounded context. Code should stay
 * within its bounded context and not navigate up the path hierarchy excessively.
 */
public class MS020BoundedContextScope extends AbstractStructureRule {

  private static final int MIN_PARENT_CHAIN_LENGTH = 3;

  public MS020BoundedContextScope() {
    super("MS-020");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    return !module.isParent();
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    List<String> violatingFiles = new ArrayList<>();

    for (ModuleInfo.SubModuleInfo subModule : module.getSubModules().values()) {
      if (subModule.isExists() && subModule.getJavaSourceFiles() != null) {
        for (Path javaFile : subModule.getJavaSourceFiles()) {
          Optional<CompilationUnit> cuOpt = parseJavaFile(javaFile);
          if (cuOpt.isEmpty()) {
            continue;
          }

          CompilationUnit cu = cuOpt.get();

          // Find all method call chains
          boolean hasExcessiveParentChain =
              cu.findAll(MethodCallExpr.class).stream()
                  .filter(call -> "getParent".equals(call.getNameAsString()))
                  .anyMatch(call -> countParentChain(call) >= MIN_PARENT_CHAIN_LENGTH);

          if (hasExcessiveParentChain) {
            String relativePath = module.getBasePath().relativize(javaFile).toString();
            violatingFiles.add(relativePath);
          }
        }
      }
    }

    if (violatingFiles.isEmpty()) {
      return List.of();
    }

    String found =
        violatingFiles.size()
            + " file(s) with excessive .getParent() chains: "
            + String.join(
                ", ", violatingFiles.subList(0, Math.min(MAX_VIOLATIONS, violatingFiles.size())));
    if (violatingFiles.size() > MAX_VIOLATIONS) {
      found += " ...and " + (violatingFiles.size() - MAX_VIOLATIONS) + " more";
    }

    return List.of(
        createViolation(
            module,
            "Excessive .getParent() chains found reaching outside bounded context. " + found));
  }

  private int countParentChain(MethodCallExpr call) {
    int count = 1;
    var scope = call.getScope();
    while (scope.isPresent() && scope.get() instanceof MethodCallExpr) {
      MethodCallExpr scopeCall = (MethodCallExpr) scope.get();
      if ("getParent".equals(scopeCall.getNameAsString())) {
        count++;
        scope = scopeCall.getScope();
      } else {
        break;
      }
    }
    return count;
  }
}
