# rake initial_setup:test_collection
# rake initial_setup:test_sites

def add_site(collection, current_user, site_params)
  site_params = site_params.with_indifferent_access
  site = collection.sites.new(user: current_user)
  site.validate_and_process_parameters(site_params, current_user)
  site.assign_default_values_for_create
  site.save!
end

namespace :initial_setup do
  task test_collection: :environment do
    admin = User.create! email: "admin@instedd.org", password: "secret", confirmed_at: Time.now.beginning_of_day
    collection = admin.create_collection(Collection.new({name: "Kenya Facilities", icon: "default", anonymous_name_permission: "none", anonymous_location_permission: "none"}))
    layer = admin.create_layer_for(collection, name: "general", ord: 1)

    layer.update_attributes!({
      fields_attributes: [
        # {name: "atext", code: "atext", kind: "text", ord: "1"},
        # {name: "anumber", code: "anumber", kind: "numeric", ord: "2", config: { allows_decimals: "false" }},
        {name: "f_type", code: "f_type", kind: "select_one", ord: "1", config: {
          options: [
            {id: 1, code: "dispensary", label: "Dispensary"},
            {id: 2, code: "health_centre", label: "Health Centre"},
            {id: 3, code: "clinic", label: "Clinic"},
            {id: 4, code: "general_hospital", label: "General Hospital"},
            {id: 5, code: "hospital", label: "Hospital"},
          ]}
        }
      ]
    })
  end

  task test_sites: :environment do
    admin = User.find_by_email "admin@instedd.org"
    collection = admin.collections.first

    f_type = { "Dispensary" => 1 , "Health Centre" => 2 , "Clinic" => 3 , "General Hospital" => 4 , "Hospital" => 5 }

    CSV.foreach "#{__dir__}/kenya-facilities.csv", headers: true do |row|
      add_site collection, admin, {
        name: row["name"],
        lng: row["long"].to_f, lat: row["lat"].to_f,
        properties: {
          f_type: f_type[row["f_type"]]
        }
      }
    end

    # add_site col1, admin, {
    #   name: "lorem site",
    #   lng: 33, lat: 44,
    #   properties: {
    #     atext: "lorem", anumber: 42, achoose: 2
    #   }
    # }
  end
end
