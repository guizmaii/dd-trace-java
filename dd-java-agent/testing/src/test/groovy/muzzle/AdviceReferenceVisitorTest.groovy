package muzzle

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import datadog.trace.agent.tooling.muzzle.Reference
import datadog.trace.agent.tooling.muzzle.ReferenceCreator
import datadog.trace.agent.tooling.muzzle.ReferenceMatcher

class AdviceReferenceVisitorTest extends AgentTestRunner {

  def "methods body references"() {
    setup:
    Map<String, Reference> references = ReferenceCreator.createReferencesFrom(AdviceClass.getName(), this.getClass().getClassLoader())

    expect:
    references.get('java.lang.Object') != null
    references.get('muzzle.AdviceClass$A') != null
    references.get('muzzle.AdviceClass$SomeInterface') != null
    references.get('muzzle.AdviceClass$SomeImplementation') != null
    references.keySet().size() == 4
  }

  def "match safe classpaths"() {
    setup:
    Reference[] refs = ReferenceCreator.createReferencesFrom(AdviceClass.getName(), this.getClass().getClassLoader()).values().toArray(new Reference[0])
    ReferenceMatcher refMatcher = new ReferenceMatcher(refs)
    ClassLoader safeClassloader = new URLClassLoader([TestUtils.createJarWithClasses(AdviceClass$A,
      AdviceClass$SomeInterface,
      AdviceClass$SomeImplementation)] as URL[],
      (ClassLoader) null)
    ClassLoader unsafeClassloader = new URLClassLoader([TestUtils.createJarWithClasses(AdviceClass$SomeInterface,
      AdviceClass$SomeImplementation)] as URL[],
      (ClassLoader) null)

    expect:
    refMatcher.getMismatchedReferenceSources(safeClassloader).size() == 0
    refMatcher.getMismatchedReferenceSources(unsafeClassloader).size() == 1
  }
}
