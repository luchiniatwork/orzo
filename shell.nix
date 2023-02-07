{
  pkgs ? import <nixpkgs> {
    config = { allowUnfree = true; };
  }
}:
let
  buildInputs = import ./packages.nix;
in
pkgs.mkShell {
  buildInputs = buildInputs;
}
