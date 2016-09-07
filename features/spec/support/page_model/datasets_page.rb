class DatasetsPage < SitePrism::Page
  set_url '/datasets'

  element :primary, ".primary"
  element :approve, :button, 'Approve'
  element :authorise, :button, 'Authorise'
  element :import, :button, 'Import'


  def press_primary_button
    primary.click
    wait_for_submit
  end

  def wait_for_submit
    sleep 0.5
  end

  def press_delete_dataset_button
    page.find(".dataset-card").click
    page.find(".delete").click
  end
end