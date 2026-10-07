/**
 * Classes written to break the architecture rules, one or more per rule. They sit outside the
 * application's base package so that component scanning, entity scanning and the production
 * architecture test never see them; only {@code ArchitectureRulesBiteTest} imports them.
 */
package com.registro.archfixture;
