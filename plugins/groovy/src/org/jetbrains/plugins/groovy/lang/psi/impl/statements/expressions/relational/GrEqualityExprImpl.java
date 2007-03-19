package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.relational;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrEqualityExprImpl extends GroovyPsiElementImpl implements GrBinaryExpression {

  public GrEqualityExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Equality expression";
  }

}