let
  tag = "22.11";
  nixpkgs = fetchTarball "https://github.com/NixOS/nixpkgs/archive/refs/tags/${tag}.tar.gz";
  pkgs = import nixpkgs {
    config = { allowUnfree = true; };
  };
in with pkgs; [
  # Runtimes
  babashka
  clojure
  maven

  # keep this line if you use bash
  bashInteractive
]
