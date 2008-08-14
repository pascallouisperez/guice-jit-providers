package com.googlecode.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;

public class PackageVisilibityTestModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(PackagePrivateInterface.class).to(PackagePrivateImpl.class);
  }

  public static class PublicUserOfPackagePrivate {
    @Inject public PublicUserOfPackagePrivate(PackagePrivateInterface ppi) {}
  }

  interface PackagePrivateInterface {}

  static class PackagePrivateImpl implements PackagePrivateInterface {}
}
