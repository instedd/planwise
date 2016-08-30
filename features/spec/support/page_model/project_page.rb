class ProjectPage < SitePrism::Page
  set_url '/projects/{id}'

  element :delete, :button, 'Delete project'
end