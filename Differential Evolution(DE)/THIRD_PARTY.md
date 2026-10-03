# Bundled dependencies

The source of this generator is provided with the experiment. Third-party JARs retain their own licences and embedded metadata/notices.

| Dependency | Version | Upstream / licence information |
|---|---|---|
| JaCoCo agent + core | 0.8.12 | https://www.jacoco.org/jacoco/ — Eclipse Public License 2.0 |
| ASM, ASM Commons, ASM Tree | 9.7 | https://asm.ow2.io/ — BSD licence |
| JUnit | 4.13.2 | https://junit.org/junit4/ — Eclipse Public License 1.0 |
| Hamcrest Core | 1.3 | https://hamcrest.org/ — BSD licence |

Artifacts come from Maven Central. `Configuration/lib/sha256.json` records the exact bundled files. `Code/setup.py` checks those hashes before compiling. A missing dependency is retrieved from its pinned Maven artifact URL and checked against the repository checksum.

JUnit and Hamcrest are used for the generator's local fixture checks. Defects4J supplies the project-specific JUnit classpath for final benchmark execution. Defects4J/project repositories and their source code are not included in this ZIP.
