project = "UltiObserver"
author = "Mike Jarvis"
copyright = "2026, Mike Jarvis"

extensions = [
    "sphinx.ext.autosectionlabel",
]

default_role = "any"

templates_path = ["_templates"]
exclude_patterns = ["_build", "Thumbs.db", ".DS_Store"]

html_theme = "furo"
html_title = "UltiObserver"
html_static_path = ["_static"]
html_css_files = ["docs-layout.css"]
html_theme_options = {
    "source_repository": "https://github.com/rmjarvis/UltiObserver/",
    "source_branch": "main",
    "source_directory": "docs/",
}

rst_epilog = """
.. |issues| replace:: GitHub Issues
.. _GitHub Issues: https://github.com/rmjarvis/UltiObserver/issues
"""
