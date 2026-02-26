{
  description = "Minecraft event plugin development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
      in {
        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            jdk21
            maven
          ];

          shellHook = ''
            echo "=== Minecraft Plugin Dev Environment ==="
            echo "Java:  $(java -version 2>&1 | head -1)"
            echo "Maven: $(mvn --version | head -1)"
            echo ""
            echo "Commands:"
            echo "  mvn package          - Build the plugin JAR"
            echo "  mvn package -q       - Build quietly"
            echo ""
          '';
        };
      }
    );
}
