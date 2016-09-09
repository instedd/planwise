class ProjectHeader < SitePrism::Section
	element :delete, :button, 'Delete project'
	element :facilities, :link, 'Facilities'
	element :transport_means, :link, 'Transport Means'

	def open_facilities
		facilities.click
	end

	def open_transport_means
		transport_means.click
	end
end

class ProjectPage < SitePrism::Page
  set_url '/projects/{id}'

  section :header, ProjectHeader, '.project-header nav'
end


class ProjectFacilitiesPage < SitePrism::Page
  set_url '/projects/{id}/facilities'

  section :header, ProjectHeader, '.project-header nav'
end

class ProjectTransportMeansPage < SitePrism::Page
  set_url '/projects/{id}/transport'

  section :header, ProjectHeader, '.project-header nav'
end