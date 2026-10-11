from docutils import nodes
from sphinx.util import logging
from sphinx.util.nodes import clean_astext


project = "UltiObserver"
author = "Mike Jarvis"
copyright = "2026, Mike Jarvis"

extensions = []

# This is basically the same as sphinx.ext.autosectionlabel, but with one extra feature.
# When there is an explicit label for a section, it doesn't create the automatic label.
# This avoids duplicate label warnings when there are multiple sections with the same text.
def register_section_labels(app, document):
    """Label headings automatically only when they have no distinct explicit label."""
    domain = app.env.domains.standard_domain
    for section in document.findall(nodes.section):
        # The heading contributes one name; a distinct explicit label contributes another.
        if len(section["names"]) > 1:
            continue
        title = clean_astext(section[0])
        name = nodes.fully_normalize_name(title)
        label = (app.env.docname, section["ids"][0], title)
        if name in domain.labels and domain.labels[name][:2] != label[:2]:
            logging.getLogger(__name__).warning("duplicate section label %s", name, location=section)
        domain.anonlabels[name] = label[:2]
        domain.labels[name] = label


def setup(app):
    app.connect("doctree-read", register_section_labels)

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
