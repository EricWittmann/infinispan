package org.infinispan.objectfilter.impl.predicateindex.be;

import org.infinispan.objectfilter.impl.predicateindex.FilterEvalContext;

/**
 * @author anistor@redhat.com
 * @since 7.0
 */
public final class AndNode extends BENode {

   public AndNode(BENode parent) {
      super(parent);
   }

   @Override
   public void handleChildValue(BENode child, boolean childValue, FilterEvalContext evalContext) {
      if (isDecided(evalContext)) {
         if (evalContext.matcherContext.isSingleFilter()) {
            // for single-filter scenario we do not unsubscribe so it may happen to be notified twice
            return;
         }
         throw new IllegalStateException("This should never be called again because the state of this node has been decided already.");
      }

      if (childValue) {
         if (--evalContext.treeCounters[index] == 0) {
            // value of this node has just been decided: TRUE
            if (parent != null) {
               // let the parent know
               parent.handleChildValue(this, true, evalContext);
            } else {
               BENode[] nodes = evalContext.beTree.getNodes();
               for (int i = index; i < span; i++) {
                  nodes[i].suspendSubscription(evalContext);
               }
            }
         } else {
            // value of this node cannot be decided yet, so we cannot tell the parent anything yet but let's at least mark down the children as 'satisfied'
            evalContext.treeCounters[child.index] = BETree.EXPR_TRUE;
            BENode[] nodes = evalContext.beTree.getNodes();
            for (int i = child.index; i < child.span; i++) {
               nodes[i].suspendSubscription(evalContext);
            }
         }
      } else {
         // value of this node is decided: FALSE
         if (parent != null) {
            // let the parent know
            parent.handleChildValue(this, false, evalContext);
         } else {
            evalContext.treeCounters[0] = BETree.EXPR_FALSE;
            BENode[] nodes = evalContext.beTree.getNodes();
            for (int i = index; i < span; i++) {
               nodes[i].suspendSubscription(evalContext);
            }
         }
      }
   }
}
