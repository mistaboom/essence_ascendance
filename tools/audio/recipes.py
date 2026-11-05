"""Resolve one-level reusable layer presets; freeze both inputs and resolved DSP."""
import copy
from storage import REPO, digest, inside, read


def resolve(path, art=None):
    art = art or REPO / 'art'
    source = read(path)
    recipe = copy.deepcopy(source)
    inputs = {'recipe': {'sha256': digest(path), 'content': source}}
    library_path = recipe.pop('presetLibrary', None)
    if library_path:
        path = inside(art, library_path)
        library = read(path)
        if set(library) != {'schema', 'presets'} or library['schema'] != 1:
            raise ValueError('Unsupported preset library')
        inputs['presets'] = {'path': library_path, 'sha256': digest(path), 'content': library}
        for index, layer in enumerate(recipe['layers']):
            layer = dict(layer)
            preset = layer.pop('preset', None)
            base = copy.deepcopy(library['presets'][preset]) if preset else {}
            if 'preset' in base or 'name' in base:
                raise ValueError('Presets cannot inherit or assign track identities')
            base.update(layer)
            recipe['layers'][index] = base
    elif any('preset' in layer for layer in recipe['layers']):
        raise ValueError('Layer preset needs a library')
    return recipe, inputs
