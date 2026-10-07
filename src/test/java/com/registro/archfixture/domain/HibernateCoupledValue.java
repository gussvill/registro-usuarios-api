package com.registro.archfixture.domain;

import org.hibernate.annotations.Immutable;

/** Breaks "the domain is free of frameworks": a Hibernate annotation. */
@Immutable
public class HibernateCoupledValue {}
